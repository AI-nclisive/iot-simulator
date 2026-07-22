package com.ainclusive.iotsim.domain.replay;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.common.SchemaVersionMismatchException;
import com.ainclusive.iotsim.domain.datasource.RuntimeStartSpecs;
import com.ainclusive.iotsim.domain.run.RunCompletionEvents;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Replays a recording through a (started) data-source by streaming its value
 * timeline to the runtime. Core of the Record -> Replay flow (backend-specs/03).
 *
 * <p>Each replay opens a {@code REPLAY} {@link RunRow} and a {@code CAPTURING}
 * {@link EvidenceRow} (IS-057): the run is started, ended {@code COMPLETED} on
 * success or {@code FAILED} on error, and the evidence is seeded with the replayed
 * {@code recordingId} so {@code EvidenceService} can assemble the value timeline.
 */
@Service
public class ReplayService {

    private final DataSourceRepository dataSources;
    private final RecordingRepository recordings;
    private final ValueTimelineRepository timeline;
    private final SchemaRepository schemas;
    private final RuntimeController runtime;
    private final RunRepository runs;
    private final EvidenceRepository evidence;
    private final RuntimeEventRepository events;
    private final ObjectMapper json;

    public ReplayService(DataSourceRepository dataSources, RecordingRepository recordings,
            ValueTimelineRepository timeline, SchemaRepository schemas, RuntimeController runtime,
            RunRepository runs, EvidenceRepository evidence, RuntimeEventRepository events, ObjectMapper json) {
        this.dataSources = dataSources;
        this.recordings = recordings;
        this.timeline = timeline;
        this.schemas = schemas;
        this.runtime = runtime;
        this.runs = runs;
        this.evidence = evidence;
        this.events = events;
        this.json = json;
    }

    /**
     * Starts a replay run for {@code recordingId} on {@code dataSourceId}.
     *
     * <p>If the recording's {@code schemaVersion} differs from the data source's current schema
     * version, a {@link SchemaVersionMismatchException} is thrown unless
     * {@code compatibilityAck=true} (IS-069).
     *
     * @param deterministicSettings the seed and logical start-time for the run; if {@code null}
     *                              a random seed is chosen and captured for traceability
     */
    public ReplaySummary replay(String projectId, String dataSourceId, String recordingId,
            DeterministicSettings deterministicSettings, boolean compatibilityAck) {
        return replay(projectId, dataSourceId, recordingId, deterministicSettings, compatibilityAck, null);
    }

    /**
     * Starts a replay run linked to a parent (scenario) run.
     *
     * <p>Delegates all logic to the full overload with default {@code trigger="MANUAL"} and
     * {@code initiator="local"}.
     */
    public ReplaySummary replay(String projectId, String dataSourceId, String recordingId,
            DeterministicSettings deterministicSettings, boolean compatibilityAck, String parentRunId) {
        return replay(projectId, dataSourceId, recordingId, deterministicSettings, compatibilityAck,
                "MANUAL", "local", parentRunId);
    }

    /**
     * Starts a replay run with explicit {@code trigger}, {@code initiator}, and {@code parentRunId}.
     *
     * <p>Used by automation (e.g. {@code POST /runs}) to thread {@code trigger="AUTOMATION"} and a
     * caller-supplied initiator into the created run row.
     */
    public ReplaySummary replay(String projectId, String dataSourceId, String recordingId,
            DeterministicSettings deterministicSettings, boolean compatibilityAck,
            String trigger, String initiator, String parentRunId) {
        DataSourceRow source = requireSource(projectId, dataSourceId);
        RecordingRow recording = requireRecording(projectId, recordingId);
        ReplayGuards.requireProtocolCompatible(recording, source);

        // Load the current schema version for the compat check; the RuntimeStartSpec is built
        // from the source (which carries the simulator port) and its current schema.
        int currentSchemaVersion = schemas.findCurrent(dataSourceId)
                .map(SchemaWithNodes::version).orElse(0);

        // Schema compatibility check (IS-069): guard if the recording was captured against a
        // different schema version than the data source currently has.
        if (recording.schemaVersion() != currentSchemaVersion && !compatibilityAck) {
            throw new SchemaVersionMismatchException(recordingId, recording.schemaVersion(), currentSchemaVersion);
        }

        // Capture the seed used so the run is always reproducible from the evidence manifest.
        DeterministicSettings settings = deterministicSettings != null
                ? deterministicSettings
                : DeterministicSettings.withRandomSeed(Instant.now());

        RunRow run = runs.create(projectId, "REPLAY", trigger, initiator, List.of(dataSourceId), null, parentRunId);
        Instant startedAt = Instant.now();
        EvidenceRow evidenceRow = null;
        // Everything after the run exists is guarded: any failure (evidence setup, worker
        // launch, value streaming) must end the run FAILED rather than leave it RUNNING.
        try {
            runs.start(run.id(), now());
            evidenceRow = evidence.create(projectId, run.id(), "local");
            runs.linkEvidence(run.id(), evidenceRow.id());
            evidence.updateManifest(evidenceRow.id(), manifest(run.id(), trigger, initiator, dataSourceId,
                    startedAt, null, recordingId, settings, 0));

            RuntimeStartSpec startSpec = RuntimeStartSpecs.of(schemas, source, settings);
            List<NeutralValue> values = timeline.readAll(recordingId);
            runtime.start(dataSourceId, startSpec);
            long applied = runtime.applyValues(dataSourceId, values);
            evidence.updateManifest(evidenceRow.id(), manifest(run.id(), trigger, initiator, dataSourceId,
                    startedAt, Instant.now(), recordingId, settings, applied));
            runs.end(run.id(), "COMPLETED", now());
            RunCompletionEvents.appendTerminal(events, projectId, dataSourceId, run.id(), "COMPLETED", now());
            return new ReplaySummary(recordingId, dataSourceId, applied, run.id(), evidenceRow.id(), settings);
        } catch (RuntimeException e) {
            stampFailure(evidenceRow, run.id(), trigger, initiator, dataSourceId, startedAt, recordingId, settings);
            runs.end(run.id(), "FAILED", now());
            RunCompletionEvents.appendTerminal(events, projectId, dataSourceId, run.id(), "FAILED", now());
            throw e;
        }
    }

    /** Best-effort endedAt stamp so a failed run still shows its end time in evidence. */
    private void stampFailure(EvidenceRow evidenceRow, String runId, String trigger, String initiator,
            String dataSourceId, Instant startedAt, String recordingId, DeterministicSettings settings) {
        if (evidenceRow == null) {
            return;
        }
        try {
            evidence.updateManifest(evidenceRow.id(), manifest(runId, trigger, initiator, dataSourceId,
                    startedAt, Instant.now(), recordingId, settings, 0));
        } catch (RuntimeException ignored) {
            // advisory only; must not mask the original failure
        }
    }

    /** Evidence manifest: full run metadata + recording ref + deterministic settings. */
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

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
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

}
