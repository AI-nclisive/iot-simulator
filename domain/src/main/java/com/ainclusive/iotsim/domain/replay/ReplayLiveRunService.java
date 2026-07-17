package com.ainclusive.iotsim.domain.replay;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.common.SchemaVersionMismatchException;
import com.ainclusive.iotsim.domain.datasource.RuntimeStartSpecs;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Live-Simulate (IS-140): time-paced replay of a recording through a data-source.
 *
 * <p>Starts a {@code REPLAY} run in {@code RUNNING} state and drips recorded values to the
 * worker at the original recording pace (honouring {@code source_time} deltas). The run stays
 * {@code RUNNING} until stopped via {@link #stopIfLive} (from {@code RunService.stop}) or
 * until all values are exhausted (auto-{@code COMPLETED}).
 *
 * <p>This is the standalone-replay twin of {@code SyntheticLiveRunService}. The batch-push
 * variant ({@code ReplayService}) is kept for scenario {@code REPLAY} steps.
 */
@Service
public class ReplayLiveRunService {

    private final DataSourceRepository dataSources;
    private final RecordingRepository recordings;
    private final ValueTimelineRepository timeline;
    private final SchemaRepository schemas;
    private final RuntimeController runtime;
    private final RunRepository runs;
    private final EvidenceRepository evidence;
    private final ObjectMapper json;
    private final Clock wallClock;

    private final ConcurrentMap<String, LiveReplay> registry = new ConcurrentHashMap<>();

    @Autowired
    public ReplayLiveRunService(DataSourceRepository dataSources, RecordingRepository recordings,
            ValueTimelineRepository timeline, SchemaRepository schemas, RuntimeController runtime,
            RunRepository runs, EvidenceRepository evidence, ObjectMapper json) {
        this(dataSources, recordings, timeline, schemas, runtime, runs, evidence, json, Clock.systemUTC());
    }

    /** Test seam: inject a controllable wall clock. */
    ReplayLiveRunService(DataSourceRepository dataSources, RecordingRepository recordings,
            ValueTimelineRepository timeline, SchemaRepository schemas, RuntimeController runtime,
            RunRepository runs, EvidenceRepository evidence, ObjectMapper json, Clock wallClock) {
        this.dataSources = dataSources;
        this.recordings = recordings;
        this.timeline = timeline;
        this.schemas = schemas;
        this.runtime = runtime;
        this.runs = runs;
        this.evidence = evidence;
        this.json = json;
        this.wallClock = wallClock;
    }

    /**
     * Starts a live replay run. Returns immediately with run in {@code RUNNING} state;
     * values are dripped to the worker on each {@link #tickAll()} at the original recording pace.
     */
    public ReplaySummary start(String projectId, String dataSourceId, String recordingId,
            DeterministicSettings deterministicSettings, boolean compatibilityAck,
            String trigger, String initiator) {
        DataSourceRow source = requireSource(projectId, dataSourceId);
        RecordingRow recording = requireRecording(projectId, recordingId);
        ReplayGuards.requireProtocolCompatible(recording, source);

        int currentSchemaVersion = schemas.findCurrent(dataSourceId)
                .map(SchemaWithNodes::version).orElse(0);
        if (recording.schemaVersion() != currentSchemaVersion && !compatibilityAck) {
            throw new SchemaVersionMismatchException(recordingId, recording.schemaVersion(), currentSchemaVersion);
        }

        DeterministicSettings settings = deterministicSettings != null
                ? deterministicSettings
                : DeterministicSettings.withRandomSeed(wallClock.instant());

        List<NeutralValue> values = new ArrayList<>(timeline.readAll(recordingId));
        values.sort(Comparator.comparing(NeutralValue::sourceTime));

        RunRow run = runs.create(projectId, "REPLAY", trigger, initiator, List.of(dataSourceId), null, null);
        Instant startedAt = wallClock.instant();
        try {
            runs.start(run.id(), now());
            EvidenceRow evidenceRow = evidence.create(projectId, run.id(), initiator);
            runs.linkEvidence(run.id(), evidenceRow.id());
            evidence.updateManifest(evidenceRow.id(),
                    manifest(run.id(), trigger, initiator, dataSourceId, startedAt, null, recordingId, settings, 0));

            RuntimeStartSpec startSpec = RuntimeStartSpecs.of(schemas, source, settings);
            runtime.start(dataSourceId, startSpec);
            // IMPORT sources store importRecordingId in runtimeConfig; don't overwrite it.
            if (!"IMPORT".equals(source.basis())) {
                saveLastRecording(dataSourceId, recordingId);
            }

            if (values.isEmpty()) {
                // Nothing to replay — complete immediately.
                runtime.stop(dataSourceId);
                stampAndEnd(run.id(), evidenceRow.id(), trigger, initiator, dataSourceId, startedAt,
                        recordingId, settings, 0, "COMPLETED");
                return new ReplaySummary(recordingId, dataSourceId, 0, run.id(), evidenceRow.id(), settings);
            }

            Instant originSourceTime = values.get(0).sourceTime();
            registry.put(run.id(), new LiveReplay(run.id(), evidenceRow.id(), dataSourceId,
                    recordingId, trigger, initiator, startedAt, settings, startedAt, originSourceTime, values));

            return new ReplaySummary(recordingId, dataSourceId, values.size(),
                    run.id(), evidenceRow.id(), settings);
        } catch (RuntimeException e) {
            runs.end(run.id(), "FAILED", now());
            throw e;
        }
    }

    /** Convenience overload for manual (UI-triggered) replays. */
    public ReplaySummary start(String projectId, String dataSourceId, String recordingId,
            DeterministicSettings deterministicSettings, boolean compatibilityAck) {
        return start(projectId, dataSourceId, recordingId, deterministicSettings, compatibilityAck,
                "MANUAL", "local");
    }

    /** Advances every registered live replay by one pacing step (called by the pacer, ~every 250 ms). */
    public void tickAll() {
        for (LiveReplay live : new ArrayList<>(registry.values())) {
            tickOne(live);
        }
    }

    private void tickOne(LiveReplay live) {
        try {
            long elapsedMs = live.elapsedMs(wallClock);
            Instant due = live.originSourceTime.plusMillis(elapsedMs);

            List<NeutralValue> batch = new ArrayList<>();
            int cursor = live.cursor;
            while (cursor < live.values.size()
                    && !live.values.get(cursor).sourceTime().isAfter(due)) {
                batch.add(live.values.get(cursor));
                cursor++;
            }
            live.cursor = cursor;

            if (!batch.isEmpty()) {
                runtime.applyValues(live.dataSourceId, batch);
            }
            if (cursor >= live.values.size()) {
                finalizeRun(live, "COMPLETED");
            }
        } catch (RuntimeException e) {
            finalizeRun(live, "FAILED");
        }
    }

    /**
     * Cancels a live replay if present. Does NOT end the run or stop the runtime —
     * the caller ({@code RunService.stop}) owns those transitions.
     * Returns whether a live replay was actually cancelled (idempotent).
     */
    public boolean stopIfLive(String runId) {
        LiveReplay live = registry.remove(runId);
        if (live == null) {
            return false;
        }
        softStampEvidence(live);
        return true;
    }

    private void finalizeRun(LiveReplay live, String terminalState) {
        if (registry.remove(live.runId) == null) {
            return; // already finalized/stopped
        }
        runtime.stop(live.dataSourceId);
        softStampEvidence(live);
        runs.end(live.runId, terminalState, now());
    }

    /**
     * Best-effort evidence stamp. Swallowed on failure — the value count is advisory,
     * and a transient error must not block the terminal-state transition.
     */
    private void softStampEvidence(LiveReplay live) {
        try {
            evidence.updateManifest(live.evidenceId,
                    manifest(live.runId, live.trigger, live.initiator, live.dataSourceId,
                            live.startedAt, null, live.recordingId, live.settings, live.cursor));
        } catch (RuntimeException ignored) {
            // advisory count only
        }
    }

    private void saveLastRecording(String dataSourceId, String recordingId) {
        try {
            dataSources.saveRuntimeConfig(dataSourceId,
                    json.writeValueAsString(Map.of("lastRecordingId", recordingId)));
        } catch (RuntimeException ignored) {
            // best-effort — UI default, not a hard requirement
        }
    }

    private void stampAndEnd(String runId, String evidenceId, String trigger, String initiator,
            String dataSourceId, Instant startedAt, String recordingId,
            DeterministicSettings settings, long valueCount, String terminalState) {
        try {
            evidence.updateManifest(evidenceId,
                    manifest(runId, trigger, initiator, dataSourceId, startedAt, wallClock.instant(),
                            recordingId, settings, valueCount));
        } catch (RuntimeException ignored) {
            // advisory
        }
        runs.end(runId, terminalState, now());
    }

    private String manifest(String runId, String trigger, String initiator, String dataSourceId,
            Instant startedAt, Instant endedAt, String recordingId,
            DeterministicSettings settings, long valueCount) {
        LinkedHashMap<String, Object> m = new LinkedHashMap<>();
        m.put("kind", "REPLAY");
        m.put("runId", runId);
        m.put("trigger", trigger);
        m.put("initiator", initiator);
        m.put("startedAt", startedAt.toString());
        m.put("endedAt", endedAt != null ? endedAt.toString() : null);
        m.put("sourceIds", List.of(dataSourceId));
        m.put("scenarioId", null);
        m.put("recordingId", recordingId);
        m.put("seed", settings.seed());
        m.put("valueCount", valueCount);
        return json.writeValueAsString(m);
    }

    private DataSourceRow requireSource(String projectId, String dataSourceId) {
        return dataSources.findById(dataSourceId)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("DataSource", dataSourceId));
    }

    private RecordingRow requireRecording(String projectId, String recordingId) {
        return recordings.findById(recordingId)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Recording", recordingId));
    }

    /**
     * Recordings are scoped to a protocol type, not to the data-source instance they were
     * captured from (IS-160): replay is allowed against any data source of a compatible
     * protocol, and rejected with a clear 400 otherwise.
     */
    private OffsetDateTime now() {
        return OffsetDateTime.ofInstant(wallClock.instant(), ZoneOffset.UTC);
    }

    /** In-memory handle for one running live replay. */
    static final class LiveReplay {
        final String runId;
        final String evidenceId;
        final String dataSourceId;
        final String recordingId;
        final String trigger;
        final String initiator;
        final Instant startedAt;
        final DeterministicSettings settings;
        final Instant replayStartWall;
        final Instant originSourceTime;
        final List<NeutralValue> values;
        // Written only by the pacer thread in tickOne; read by a request thread in stopIfLive.
        // volatile gives the single-writer/reader hand-off a happens-before edge.
        volatile int cursor;

        LiveReplay(String runId, String evidenceId, String dataSourceId, String recordingId,
                String trigger, String initiator, Instant startedAt,
                DeterministicSettings settings, Instant replayStartWall, Instant originSourceTime,
                List<NeutralValue> values) {
            this.runId = runId;
            this.evidenceId = evidenceId;
            this.dataSourceId = dataSourceId;
            this.recordingId = recordingId;
            this.trigger = trigger;
            this.initiator = initiator;
            this.startedAt = startedAt;
            this.settings = settings;
            this.replayStartWall = replayStartWall;
            this.originSourceTime = originSourceTime;
            this.values = values;
            this.cursor = 0;
        }

        long elapsedMs(Clock clock) {
            return Duration.between(replayStartWall, clock.instant()).toMillis();
        }
    }
}
