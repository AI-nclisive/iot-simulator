package com.ainclusive.iotsim.domain.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.common.SchemaVersionMismatchException;
import com.ainclusive.iotsim.domain.run.FakeRuntimeEventRepository;
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
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import com.ainclusive.iotsim.protocolmodel.MutableClock;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueFilter;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ReplayLiveRunServiceTest {

    private static final String PROJECT = "p1";
    private static final String SOURCE = "ds1";
    private static final String RECORDING = "rec1";
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final ObjectMapper json = new ObjectMapper();
    private CapturingRuntime runtime;
    private FakeRuns runs;
    private FakeEvidence evidence;
    private FakeRuntimeEventRepository events;
    private FakeDataSources dataSources;
    private MutableClock wall;

    @BeforeEach
    void setUp() {
        runtime = new CapturingRuntime();
        runs = new FakeRuns();
        evidence = new FakeEvidence();
        events = new FakeRuntimeEventRepository();
        dataSources = new FakeDataSources(SOURCE, PROJECT);
        wall = MutableClock.at(T0, ZoneOffset.UTC);
    }

    // Values at T+0s, T+1s, T+2s — pacing tests rely on 1-second spacing.
    private List<NeutralValue> defaultValues() {
        return List.of(
                NeutralValue.good("temp", T0, 1.0),
                NeutralValue.good("temp", T0.plusSeconds(1), 2.0),
                NeutralValue.good("temp", T0.plusSeconds(2), 3.0));
    }

    private ReplayLiveRunService service() {
        return service(new FakeTimeline(defaultValues()));
    }

    private ReplayLiveRunService service(ValueTimelineRepository timeline) {
        return service(timeline, runtime);
    }

    private ReplayLiveRunService service(ValueTimelineRepository timeline, RuntimeController rt) {
        return new ReplayLiveRunService(
                dataSources,
                new FakeRecordings(RECORDING, PROJECT),
                timeline,
                new EmptySchemas(),
                rt, runs, evidence, events, json, wall);
    }

    private ReplayLiveRunService serviceWithRecordingVersion(int schemaVersion) {
        return new ReplayLiveRunService(
                new FakeDataSources(SOURCE, PROJECT),
                new FakeRecordingsAtVersion(RECORDING, PROJECT, schemaVersion),
                new FakeTimeline(defaultValues()),
                new EmptySchemas(),
                runtime, runs, evidence, events, json, wall);
    }

    // --- tests ---

    @Test
    void startRejectsRecordingWhoseProtocolDiffersFromTargetSource() {
        // dataSources' SOURCE is OPC_UA; a MODBUS_TCP recording is not compatible,
        // even with compatibilityAck=true (protocol mismatch is not schema-version drift).
        ReplayLiveRunService svc = new ReplayLiveRunService(
                dataSources,
                new FakeRecordingsWithProtocol(RECORDING, PROJECT, "MODBUS_TCP"),
                new FakeTimeline(defaultValues()),
                new EmptySchemas(),
                runtime, runs, evidence, events, json, wall);

        assertThatThrownBy(() -> svc.start(PROJECT, SOURCE, RECORDING, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MODBUS_TCP")
                .hasMessageContaining("OPC_UA");
    }

    @Test
    void startOpensRunningRunAndEvidenceAndReturnsImmediately() {
        ReplayLiveRunService svc = service();

        ReplaySummary summary = svc.start(PROJECT, SOURCE, RECORDING, null, true);

        assertThat(summary.valueCount()).isEqualTo(3);
        assertThat(summary.dataSourceId()).isEqualTo(SOURCE);
        assertThat(summary.recordingId()).isEqualTo(RECORDING);
        assertThat(summary.runId()).isNotBlank();
        assertThat(summary.evidenceId()).isNotBlank();
        assertThat(summary.deterministicSettings()).isNotNull();

        RunRow run = runs.byId.get(summary.runId());
        assertThat(run.kind()).isEqualTo("REPLAY");
        assertThat(run.state()).isEqualTo("RUNNING");
        assertThat(run.trigger()).isEqualTo("MANUAL");
        assertThat(run.initiator()).isEqualTo("local");
        assertThat(run.sourceIds()).containsExactly(SOURCE);
        assertThat(run.evidenceId()).isEqualTo(summary.evidenceId());

        String manifest = evidence.byId.get(summary.evidenceId()).manifestJson();
        assertThat(manifest).contains("\"kind\":\"REPLAY\"");
        assertThat(manifest).contains("\"trigger\":\"MANUAL\"");
        assertThat(manifest).contains("\"initiator\":\"local\"");
        assertThat(manifest).contains("\"startedAt\":");
        assertThat(manifest).contains("\"sourceIds\":[\"" + SOURCE + "\"]");
        assertThat(manifest).contains("\"recordingId\":\"" + RECORDING + "\"");
        assertThat(manifest).contains("\"seed\":");

        // Worker started; no values emitted until first tick.
        assertThat(runtime.state(SOURCE)).isEqualTo("RUNNING");
        assertThat(runtime.applied).isEmpty();
    }

    @Test
    void tickAllDripsValuesBySourceTimeDelta() {
        ReplayLiveRunService svc = service();
        ReplaySummary summary = svc.start(PROJECT, SOURCE, RECORDING, null, true);

        // Elapsed=0: due=T+0s → value[0] at T+0s is emitted.
        svc.tickAll();
        assertThat(runtime.applied).hasSize(1);
        assertThat(runs.byId.get(summary.runId()).state()).isEqualTo("RUNNING");

        // Advance 1s: elapsed=1000ms, due=T+1s → value[1] emitted.
        wall.advance(Duration.ofSeconds(1));
        svc.tickAll();
        assertThat(runtime.applied).hasSize(2);
        assertThat(runs.byId.get(summary.runId()).state()).isEqualTo("RUNNING");

        // Advance 1s more: elapsed=2000ms, due=T+2s → value[2] emitted and run completes.
        wall.advance(Duration.ofSeconds(1));
        svc.tickAll();
        assertThat(runtime.applied).hasSize(3);
        assertThat(runs.byId.get(summary.runId()).state()).isEqualTo("COMPLETED");
        // IS-179: auto-completion must stamp a real endedAt, not null.
        assertThat(evidence.byId.get(summary.evidenceId()).manifestJson())
                .doesNotContain("\"endedAt\":null");
    }

    @Test
    void allValuesExhaustedAutoCompletesRunAndStopsRuntime() {
        ReplayLiveRunService svc = service();
        ReplaySummary summary = svc.start(PROJECT, SOURCE, RECORDING, null, true);

        // Jump well past the last source_time — all 3 values due at once.
        wall.advance(Duration.ofSeconds(10));
        svc.tickAll();

        assertThat(runtime.applied).hasSize(3);
        assertThat(runs.byId.get(summary.runId()).state()).isEqualTo("COMPLETED");
        // Auto-complete must stop the runtime (unlike stopIfLive, which leaves it to RunService).
        assertThat(runtime.state(SOURCE)).isEqualTo("STOPPED");
        assertThat(evidence.byId.get(summary.evidenceId()).manifestJson()).contains("\"valueCount\":3");
        // IS-179: auto-completion must stamp a real endedAt, not null.
        assertThat(evidence.byId.get(summary.evidenceId()).manifestJson())
                .doesNotContain("\"endedAt\":null")
                .contains("\"endedAt\":\"" + wall.instant() + "\"");
        // IS-182: auto-completion is mirrored into runtime_events.
        assertThat(events.appended).extracting("type").containsExactly("RUN_COMPLETED");
        // IS-187: a completed run's evidence leaves CAPTURING for READY.
        assertThat(evidence.byId.get(summary.evidenceId()).status()).isEqualTo("READY");

        // A subsequent tick is a no-op — removed from registry.
        svc.tickAll();
        assertThat(runtime.applied).hasSize(3);
    }

    @Test
    void stopIfLiveCancelsFeedAndStampsEvidenceButLeavesRunEndingToCaller() {
        ReplayLiveRunService svc = service();
        ReplaySummary summary = svc.start(PROJECT, SOURCE, RECORDING, null, true);

        // Emit first value.
        svc.tickAll();
        assertThat(runtime.applied).hasSize(1);

        wall.advance(Duration.ofMillis(1)); // so stop's endedAt is distinguishable from startedAt
        boolean wasLive = svc.stopIfLive(summary.runId());

        assertThat(wasLive).isTrue();
        // Evidence stamped with current cursor position.
        assertThat(evidence.byId.get(summary.evidenceId()).manifestJson()).contains("\"valueCount\":1");
        // IS-179: manual stop must stamp the real endedAt, not null.
        assertThat(evidence.byId.get(summary.evidenceId()).manifestJson())
                .doesNotContain("\"endedAt\":null")
                .contains("\"endedAt\":\"" + wall.instant() + "\"");
        // Run NOT ended here — RunService owns that on manual stop.
        assertThat(runs.byId.get(summary.runId()).state()).isEqualTo("RUNNING");
        // Runtime NOT stopped — RunService owns that too.
        assertThat(runtime.state(SOURCE)).isEqualTo("RUNNING");
        // IS-187: stopIfLive doesn't own the terminal state, so evidence status must
        // stay CAPTURING until RunService.stop actually ends the run.
        assertThat(evidence.byId.get(summary.evidenceId()).status()).isEqualTo("CAPTURING");

        // Subsequent ticks are no-ops.
        wall.advance(Duration.ofSeconds(5));
        svc.tickAll();
        assertThat(runtime.applied).hasSize(1);

        // Idempotent second stop.
        assertThat(svc.stopIfLive(summary.runId())).isFalse();
    }

    @Test
    void stopIfLiveOnUnknownRunIdReturnsFalse() {
        ReplayLiveRunService svc = service();
        assertThat(svc.stopIfLive("nonexistent-run")).isFalse();
    }

    @Test
    void schemaVersionMismatchWithoutAckThrowsAndOpensNoRun() {
        // FakeRecordings returns schemaVersion=1; EmptySchemas yields currentVersion=0 → mismatch.
        ReplayLiveRunService svc = serviceWithRecordingVersion(1);

        assertThatThrownBy(() -> svc.start(PROJECT, SOURCE, RECORDING, null, false))
                .isInstanceOf(SchemaVersionMismatchException.class);

        assertThat(runs.byId).isEmpty();
    }

    @Test
    void schemaVersionMismatchWithAckProceedsNormally() {
        ReplayLiveRunService svc = serviceWithRecordingVersion(1);

        ReplaySummary summary = svc.start(PROJECT, SOURCE, RECORDING, null, true);

        assertThat(summary.valueCount()).isEqualTo(3);
        assertThat(runs.byId.get(summary.runId()).state()).isEqualTo("RUNNING");
    }

    @Test
    void workerStartFailureEndsRunFailed() {
        ReplayLiveRunService svc = service(new FakeTimeline(defaultValues()), new ThrowingRuntime());

        assertThatThrownBy(() -> svc.start(PROJECT, SOURCE, RECORDING, null, true))
                .isInstanceOf(IllegalStateException.class);

        assertThat(runs.byId.values()).singleElement()
                .extracting(RunRow::state).isEqualTo("FAILED");
        // IS-182: start failure is mirrored into runtime_events.
        assertThat(events.appended).extracting("type").containsExactly("RUN_FAILED");
        // IS-187: a failed run's evidence leaves CAPTURING for PARTIAL, not READY.
        assertThat(evidence.byId.values()).singleElement()
                .extracting(EvidenceRow::status).isEqualTo("PARTIAL");
    }

    @Test
    void missingRecordingThrowsWithoutOpeningRun() {
        ReplayLiveRunService svc = new ReplayLiveRunService(
                new FakeDataSources(SOURCE, PROJECT),
                new FakeRecordings("other-rec", PROJECT), // RECORDING not found
                new FakeTimeline(defaultValues()),
                new EmptySchemas(),
                runtime, runs, evidence, events, json, wall);

        assertThatThrownBy(() -> svc.start(PROJECT, SOURCE, RECORDING, null, true))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(runs.byId).isEmpty();
    }

    @Test
    void missingDataSourceThrowsWithoutOpeningRun() {
        ReplayLiveRunService svc = new ReplayLiveRunService(
                new FakeDataSources("other-ds", PROJECT), // SOURCE not found
                new FakeRecordings(RECORDING, PROJECT),
                new FakeTimeline(defaultValues()),
                new EmptySchemas(),
                runtime, runs, evidence, events, json, wall);

        assertThatThrownBy(() -> svc.start(PROJECT, SOURCE, RECORDING, null, true))
                .isInstanceOf(ResourceNotFoundException.class);

        assertThat(runs.byId).isEmpty();
    }

    @Test
    void emptyTimelineCompletesImmediately() {
        ReplayLiveRunService svc = service(new FakeTimeline(List.of()));

        ReplaySummary summary = svc.start(PROJECT, SOURCE, RECORDING, null, true);

        assertThat(summary.valueCount()).isEqualTo(0);
        assertThat(runs.byId.get(summary.runId()).state()).isEqualTo("COMPLETED");
        // Runtime must be stopped — no leak for the empty-timeline path.
        assertThat(runtime.state(SOURCE)).isEqualTo("STOPPED");
        // IS-182: immediate auto-completion is mirrored into runtime_events.
        assertThat(events.appended).extracting("type").containsExactly("RUN_COMPLETED");
        // IS-187: a completed run's evidence leaves CAPTURING for READY.
        assertThat(evidence.byId.get(summary.evidenceId()).status()).isEqualTo("READY");
        // Nothing in registry — tick is a no-op.
        svc.tickAll();
        assertThat(runtime.applied).isEmpty();
    }

    @Test
    void startPersistsLastRecordingIdIntoRuntimeConfig() {
        ReplayLiveRunService svc = service();

        svc.start(PROJECT, SOURCE, RECORDING, null, true);

        assertThat(dataSources.savedRuntimeConfig).contains("lastRecordingId");
        assertThat(dataSources.savedRuntimeConfig).contains(RECORDING);
    }

    @Test
    void startDoesNotOverwriteRuntimeConfigForImportSource() {
        FakeDataSources importDs = new FakeDataSources(SOURCE, PROJECT, "IMPORT");
        ReplayLiveRunService svc = new ReplayLiveRunService(
                importDs,
                new FakeRecordings(RECORDING, PROJECT),
                new FakeTimeline(defaultValues()),
                new EmptySchemas(),
                runtime, runs, evidence, events, json, wall);

        svc.start(PROJECT, SOURCE, RECORDING, null, true);

        assertThat(importDs.savedRuntimeConfig).isNull();
    }

    @Test
    void startWithAutomationTriggerAndInitiatorIsReflectedInRun() {
        ReplayLiveRunService svc = service();

        ReplaySummary summary = svc.start(PROJECT, SOURCE, RECORDING, null, true, "AUTOMATION", "ci-bot");

        RunRow run = runs.byId.get(summary.runId());
        assertThat(run.trigger()).isEqualTo("AUTOMATION");
        assertThat(run.initiator()).isEqualTo("ci-bot");

        String manifest = evidence.byId.get(summary.evidenceId()).manifestJson();
        assertThat(manifest).contains("\"trigger\":\"AUTOMATION\"");
        assertThat(manifest).contains("\"initiator\":\"ci-bot\"");
    }

    @Test
    void stopIfLiveSwallowsEvidenceStampFailure() {
        ReplayLiveRunService svc = new ReplayLiveRunService(
                new FakeDataSources(SOURCE, PROJECT),
                new FakeRecordings(RECORDING, PROJECT),
                new FakeTimeline(defaultValues()),
                new EmptySchemas(),
                runtime, runs, throwingEvidenceAfterStart(), events, json, wall);
        ReplaySummary summary = svc.start(PROJECT, SOURCE, RECORDING, null, true);

        // Must not propagate to RunService.stop.
        assertThat(svc.stopIfLive(summary.runId())).isTrue();
    }

    /** Evidence that succeeds on initial manifest write but throws on subsequent stamps. */
    private FakeEvidence throwingEvidenceAfterStart() {
        return new FakeEvidence() {
            private int calls;

            @Override
            public EvidenceRow updateManifest(String id, String manifestJson) {
                if (++calls > 1) {
                    throw new IllegalStateException("evidence store unavailable");
                }
                return super.updateManifest(id, manifestJson);
            }
        };
    }

    // --- fakes ---

    private static class CapturingRuntime implements RuntimeController {
        final List<NeutralValue> applied = new ArrayList<>();
        private String state = "STOPPED";

        public String start(String id, RuntimeStartSpec spec) {
            state = "RUNNING";
            return state;
        }

        public String stop(String id) {
            state = "STOPPED";
            return state;
        }

        public String state(String id) {
            return state;
        }

        public long applyValues(String id, List<NeutralValue> values) {
            applied.addAll(values);
            return values.size();
        }

        public SourceHealth health(String id) {
            return new SourceHealth(state, null);
        }
    }

    private static final class ThrowingRuntime implements RuntimeController {
        public String start(String id, RuntimeStartSpec spec) {
            throw new IllegalStateException("worker launch failed");
        }

        public String stop(String id) {
            return "STOPPED";
        }

        public String state(String id) {
            return "STOPPED";
        }

        public long applyValues(String id, List<NeutralValue> values) {
            return 0;
        }

        public SourceHealth health(String id) {
            return new SourceHealth("STOPPED", null);
        }
    }

    private static class FakeDataSources implements DataSourceRepository {
        final String id;
        final String projectId;
        final String basis;
        String savedRuntimeConfig;

        FakeDataSources(String id, String projectId) {
            this(id, projectId, "MANUAL");
        }

        FakeDataSources(String id, String projectId, String basis) {
            this.id = id;
            this.projectId = projectId;
            this.basis = basis;
        }

        public Optional<DataSourceRow> findById(String id) {
            if (!this.id.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new DataSourceRow(id, projectId, "src", "OPC_UA", basis,
                    null, null, 0, null, "{}", null, false, now, now, "local", 0));
        }

        public void saveRuntimeConfig(String id, String runtimeConfigJson) {
            this.savedRuntimeConfig = runtimeConfigJson;
        }

        public DataSourceRow insert(String p, String n, String pr, String b, int sp, String rde,
                String rc, String sc, String c) {
            throw new UnsupportedOperationException();
        }

        public List<DataSourceRow> findByProject(String projectId) {
            return List.of();
        }

        public List<DataSourceRow> findByProjectPaged(String projectId, String protocol,
                OffsetDateTime afterAt, String afterId, int limit) {
            return List.of();
        }

        public Optional<DataSourceRow> update(String i, String n, int sp, String rde, String rc,
                String sc, boolean en, long v) {
            throw new UnsupportedOperationException();
        }

        public Optional<DataSourceRow> duplicate(String sourceId, String newName, String createdBy) {
            throw new UnsupportedOperationException();
        }

        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }
    }

    private record FakeRecordings(String id, String projectId) implements RecordingRepository {
        public Optional<RecordingRow> findById(String id) {
            if (!this.id.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new RecordingRow(id, projectId, "ds1", "OPC_UA", 1, "SCAN_RECORD",
                    "SCHEMA_AND_DATA", null, null, null, 0, 0, now, now, "local", 0, "[]"));
        }

        public RecordingRow create(String p, String d, String pr, int sv, String o, String st, String n, String c, String sj) {
            throw new UnsupportedOperationException();
        }

        public List<RecordingRow> findByProject(String projectId) {
            return List.of();
        }

        public List<RecordingRow> findByProjectPaged(String projectId,
                OffsetDateTime afterAt, String afterId, int limit) {
            return List.of();
        }

        public RecordingRow finalizeStats(String i, OffsetDateTime s, OffsetDateTime e, long c, long b) {
            throw new UnsupportedOperationException();
        }

        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }

        public long countByProject(String projectId) {
            throw new UnsupportedOperationException();
        }
    }

    private record FakeRecordingsAtVersion(String id, String projectId, int schemaVersion)
            implements RecordingRepository {
        public Optional<RecordingRow> findById(String id) {
            if (!this.id.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new RecordingRow(id, projectId, "ds1", "OPC_UA", schemaVersion, "SCAN_RECORD",
                    "SCHEMA_AND_DATA", null, null, null, 0, 0, now, now, "local", 0, "[]"));
        }

        public RecordingRow create(String p, String d, String pr, int sv, String o, String st, String n, String c, String sj) {
            throw new UnsupportedOperationException();
        }

        public List<RecordingRow> findByProject(String projectId) {
            return List.of();
        }

        public List<RecordingRow> findByProjectPaged(String projectId,
                OffsetDateTime afterAt, String afterId, int limit) {
            return List.of();
        }

        public RecordingRow finalizeStats(String i, OffsetDateTime s, OffsetDateTime e, long c, long b) {
            throw new UnsupportedOperationException();
        }

        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }

        public long countByProject(String projectId) {
            throw new UnsupportedOperationException();
        }
    }

    /** A recording fake that returns a specific protocol (for IS-160 compat check tests). */
    private record FakeRecordingsWithProtocol(String id, String projectId, String protocol)
            implements RecordingRepository {
        public Optional<RecordingRow> findById(String id) {
            if (!this.id.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new RecordingRow(id, projectId, "ds1", protocol, 0, "SCAN_RECORD",
                    "SCHEMA_AND_DATA", null, null, null, 0, 0, now, now, "local", 0, "[]"));
        }

        public RecordingRow create(String p, String d, String pr, int sv, String o, String st, String n, String c, String sj) {
            throw new UnsupportedOperationException();
        }

        public List<RecordingRow> findByProject(String projectId) {
            return List.of();
        }

        public List<RecordingRow> findByProjectPaged(String projectId,
                OffsetDateTime afterAt, String afterId, int limit) {
            return List.of();
        }

        public RecordingRow finalizeStats(String i, OffsetDateTime s, OffsetDateTime e, long c, long b) {
            throw new UnsupportedOperationException();
        }

        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }

        public long countByProject(String projectId) {
            throw new UnsupportedOperationException();
        }
    }

    private record FakeTimeline(List<NeutralValue> values) implements ValueTimelineRepository {
        public List<NeutralValue> readAll(String recordingId) {
            return values;
        }

        public long append(String recordingId, List<NeutralValue> values) {
            throw new UnsupportedOperationException();
        }

        public List<NeutralValue> readRange(String recordingId, Instant from, Instant to) {
            return values;
        }

        public long count(String recordingId) {
            return values.size();
        }

        public List<ValueTimelineRepository.ValueTimelineEntry> readPage(String recordingId, long afterSeq, int limit,
                ValueFilter filter) {
            throw new UnsupportedOperationException();
        }

        public long sumBytes(String recordingId) {
            throw new UnsupportedOperationException();
        }

        public void deleteByRecording(String recordingId) {
            throw new UnsupportedOperationException();
        }
    }

    private record EmptySchemas() implements SchemaRepository {
        public Optional<SchemaWithNodes> findCurrent(String dataSourceId) {
            return Optional.empty();
        }

        public SchemaWithNodes saveNewVersion(String dataSourceId, List<SchemaNode> nodes) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeRuns implements RunRepository {
        final java.util.Map<String, RunRow> byId = new java.util.LinkedHashMap<>();
        private int seq;

        public RunRow create(String projectId, String kind, String trigger, String initiator,
                List<String> sourceIds, String scenarioId, String parentRunId) {
            String id = "run-" + (++seq);
            RunRow row = new RunRow(id, projectId, kind, trigger, initiator, "QUEUED",
                    scenarioId, null, null, null, OffsetDateTime.now(ZoneOffset.UTC),
                    new ArrayList<>(sourceIds), parentRunId);
            byId.put(id, row);
            return row;
        }

        public RunRow start(String id, OffsetDateTime startedAt) {
            RunRow r = byId.get(id);
            RunRow u = new RunRow(r.id(), r.projectId(), r.kind(), r.trigger(), r.initiator(),
                    "RUNNING", r.scenarioId(), r.evidenceId(), startedAt, r.endedAt(), r.createdAt(),
                    r.sourceIds(), r.parentRunId());
            byId.put(id, u);
            return u;
        }

        public RunRow end(String id, String terminalState, OffsetDateTime endedAt) {
            RunRow r = byId.get(id);
            RunRow u = new RunRow(r.id(), r.projectId(), r.kind(), r.trigger(), r.initiator(),
                    terminalState, r.scenarioId(), r.evidenceId(), r.startedAt(), endedAt, r.createdAt(),
                    r.sourceIds(), r.parentRunId());
            byId.put(id, u);
            return u;
        }

        public RunRow linkEvidence(String runId, String evidenceId) {
            RunRow r = byId.get(runId);
            RunRow u = new RunRow(r.id(), r.projectId(), r.kind(), r.trigger(), r.initiator(),
                    r.state(), r.scenarioId(), evidenceId, r.startedAt(), r.endedAt(), r.createdAt(),
                    r.sourceIds(), r.parentRunId());
            byId.put(runId, u);
            return u;
        }

        public Optional<RunRow> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        public List<RunRow> findByProject(String projectId) {
            throw new UnsupportedOperationException();
        }

        public List<RunRow> findByProjectPaged(String projectId, OffsetDateTime afterAt,
                String afterId, int limit) {
            throw new UnsupportedOperationException();
        }

        public List<RunRow> findActiveByProject(String projectId) {
            throw new UnsupportedOperationException();
        }
    }

    private static class FakeEvidence implements EvidenceRepository {
        final java.util.Map<String, EvidenceRow> byId = new java.util.LinkedHashMap<>();
        private int seq;

        public EvidenceRow create(String projectId, String runId, String createdBy) {
            String id = "ev-" + (++seq);
            EvidenceRow row = new EvidenceRow(id, projectId, runId, "CAPTURING", "{}", null,
                    OffsetDateTime.now(ZoneOffset.UTC), createdBy);
            byId.put(id, row);
            return row;
        }

        public EvidenceRow updateManifest(String id, String manifestJson) {
            EvidenceRow e = byId.get(id);
            EvidenceRow u = new EvidenceRow(e.id(), e.projectId(), e.runId(), e.status(),
                    manifestJson, e.objectRef(), e.createdAt(), e.createdBy());
            byId.put(id, u);
            return u;
        }

        public Optional<EvidenceRow> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        public Optional<EvidenceRow> findByRun(String runId) {
            return byId.values().stream().filter(e -> runId.equals(e.runId())).findFirst();
        }

        public List<EvidenceRow> findByProject(String projectId) {
            throw new UnsupportedOperationException();
        }

        public EvidenceRow updateStatus(String id, String status, String objectRef) {
            EvidenceRow e = byId.get(id);
            EvidenceRow u = new EvidenceRow(e.id(), e.projectId(), e.runId(), status,
                    e.manifestJson(), objectRef, e.createdAt(), e.createdBy());
            byId.put(id, u);
            return u;
        }

        public List<EvidenceRow> findByProjectPaged(String projectId, OffsetDateTime afterAt,
                String afterId, int limit) {
            return List.of();
        }
    }
}
