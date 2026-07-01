package com.ainclusive.iotsim.domain.synthetic;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.MutableClock;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
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

class SyntheticLiveRunServiceTest {

    private static final String PROJECT = "p1";
    private static final String SOURCE = "ds1";
    private static final Instant T0 = Instant.parse("2026-01-01T00:00:00Z");

    private final ObjectMapper json = new ObjectMapper();
    private CapturingRuntime runtime;
    private FakeRuns runs;
    private FakeEvidence evidence;
    private MutableClock wall; // injected wall clock we advance by hand

    @BeforeEach
    void setUp() {
        runtime = new CapturingRuntime();
        runs = new FakeRuns();
        evidence = new FakeEvidence();
        wall = MutableClock.at(T0, ZoneOffset.UTC);
    }

    private SyntheticLiveRunService service(String basis, SyntheticConfig config) {
        String rc = config == null ? "{}" : json.writeValueAsString(config);
        return new SyntheticLiveRunService(new FakeDataSources(basis, rc), new EmptySchemas(),
                runtime, runs, evidence, json, wall);
    }

    @Test
    void startOpensRunningRunAndEvidenceAndReturnsImmediately() {
        SyntheticLiveRunService service = service("SYNTHETIC", config(5L));

        SyntheticLiveRunSummary summary = service.start(PROJECT, SOURCE, null, "MANUAL", "local");

        assertThat(summary.seed()).isEqualTo(5L);
        assertThat(summary.state()).isEqualTo("RUNNING");
        assertThat(summary.dataSourceId()).isEqualTo(SOURCE);
        assertThat(runtime.state(SOURCE)).isEqualTo("RUNNING");
        assertThat(runtime.applied).isEmpty(); // nothing emitted until a tick

        RunRow run = runs.byId.get(summary.runId());
        assertThat(run.kind()).isEqualTo("SYNTHETIC");
        assertThat(run.state()).isEqualTo("RUNNING");
        assertThat(run.trigger()).isEqualTo("MANUAL");
        assertThat(evidence.byId.get(summary.evidenceId()).manifestJson())
                .contains("\"seed\":5").contains("\"live\":true");
        assertThat(run.evidenceId()).isEqualTo(summary.evidenceId());
    }

    private SyntheticConfig config(Long seed) {
        return new SyntheticConfig(seed, List.of(
                new SyntheticVariableConfig("temp", DataType.FLOAT64,
                        new PatternSpec("SINE", null, 0.0, 10.0, 1000L, null, null, null), 100),
                new SyntheticVariableConfig("rnd", DataType.FLOAT64,
                        new PatternSpec("RANDOM_UNIFORM", null, 0.0, 1.0, null, null, null, null), 250)));
    }

    @Test
    void tickEmitsOnlySamplesDueSinceStart() {
        SyntheticLiveRunService service = service("SYNTHETIC", config(5L));
        service.start(PROJECT, SOURCE, null, "MANUAL", "local");

        // config: temp @100ms, rnd @250ms. Advance 500ms of wall time.
        wall.advance(Duration.ofMillis(500));
        service.tickAll();

        // temp: 500/100 = 5 ; rnd: 500/250 = 2  => 7 values on first tick.
        assertThat(runtime.applied).hasSize(7);

        // Advance to 1000ms total and tick again.
        wall.advance(Duration.ofMillis(500));
        service.tickAll();

        // temp now 10 total (=> +5), rnd now 4 total (=> +2) => +7, cumulative 14.
        assertThat(runtime.applied).hasSize(14);
    }

    @Test
    void livePacedSequenceMatchesBoundedBatchForSameElapsed() {
        // Model A over 1000ms produces 14 values (see SyntheticRunServiceTest); the paced
        // feed must produce the identical ordered sequence once 1000ms has elapsed.
        CapturingRuntime batchRuntime = new CapturingRuntime();
        SyntheticRunService batch = new SyntheticRunService(
                new FakeDataSources("SYNTHETIC", json.writeValueAsString(config(5L))),
                new EmptySchemas(), batchRuntime, new FakeRuns(), new FakeEvidence(),
                json, java.time.Clock.fixed(T0, ZoneOffset.UTC));
        batch.run(PROJECT, SOURCE, 1000);

        SyntheticLiveRunService live = service("SYNTHETIC", config(5L));
        live.start(PROJECT, SOURCE, null, "MANUAL", "local");
        wall.advance(Duration.ofMillis(1000));
        live.tickAll();

        assertThat(runtime.applied).isEqualTo(batchRuntime.applied);
    }

    @Test
    void capReachedFinalizesRunCompletedWithTotalValueCount() {
        SyntheticLiveRunService service = service("SYNTHETIC", config(5L));
        SyntheticLiveRunSummary s = service.start(PROJECT, SOURCE, 1000L, "MANUAL", "local");

        wall.advance(Duration.ofMillis(5000)); // well past the 1000ms cap
        service.tickAll();

        RunRow run = runs.byId.get(s.runId());
        assertThat(run.state()).isEqualTo("COMPLETED");
        // capped at 1000ms => 14 values (temp 10 + rnd 4); count reflected in evidence.
        assertThat(runtime.applied).hasSize(14);
        assertThat(evidence.byId.get(s.evidenceId()).manifestJson()).contains("\"valueCount\":14");
        // A subsequent tick is a no-op (removed from registry).
        service.tickAll();
        assertThat(runtime.applied).hasSize(14);
    }

    @Test
    void stopIfLiveCancelsFeedAndStampsEvidenceButLeavesRunEndingToCaller() {
        SyntheticLiveRunService service = service("SYNTHETIC", config(5L));
        SyntheticLiveRunSummary s = service.start(PROJECT, SOURCE, null, "MANUAL", "local");
        wall.advance(Duration.ofMillis(500));
        service.tickAll(); // 7 emitted

        boolean wasLive = service.stopIfLive(s.runId());

        assertThat(wasLive).isTrue();
        assertThat(evidence.byId.get(s.evidenceId()).manifestJson()).contains("\"valueCount\":7");
        // Run is NOT ended here (RunService owns that on manual stop): still RUNNING.
        assertThat(runs.byId.get(s.runId()).state()).isEqualTo("RUNNING");
        // Ticking after stop does nothing.
        wall.advance(Duration.ofMillis(500));
        service.tickAll();
        assertThat(runtime.applied).hasSize(7);
        // Idempotent second stop.
        assertThat(service.stopIfLive(s.runId())).isFalse();
    }

    @Test
    void tickErrorFinalizesRunFailedAndRemovesIt() {
        // A runtime whose applyValues throws marks the run FAILED and unregisters it.
        CapturingRuntime throwing = new CapturingRuntime() {
            @Override public long applyValues(String id, List<com.ainclusive.iotsim.protocolmodel.NeutralValue> v) {
                throw new IllegalStateException("apply failed");
            }
        };
        SyntheticLiveRunService failing = new SyntheticLiveRunService(
                new FakeDataSources("SYNTHETIC", json.writeValueAsString(config(5L))),
                new EmptySchemas(), throwing, runs, evidence, json, wall);
        SyntheticLiveRunSummary s = failing.start(PROJECT, SOURCE, null, "MANUAL", "local");

        wall.advance(Duration.ofMillis(500));
        failing.tickAll(); // applyValues throws -> run FAILED

        assertThat(runs.byId.get(s.runId()).state()).isEqualTo("FAILED");
        assertThat(failing.stopIfLive(s.runId())).isFalse(); // already removed
        // feed.emitted is advanced before applyValues is called, so valueCount reflects
        // the ticks that were computed (500ms / 100ms=5 + 500ms/250ms=2 = 7), not 0.
        assertThat(evidence.byId.get(s.evidenceId()).manifestJson()).contains("\"valueCount\":7");
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

    private record FakeDataSources(String basis, String runtimeConfig) implements DataSourceRepository {
        public Optional<DataSourceRow> findById(String id) {
            if (!SOURCE.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new DataSourceRow(id, PROJECT, "Gen", "OPC_UA", basis,
                    null, null, "{}", runtimeConfig, false, now, now, "local", 0));
        }

        public DataSourceRow insert(String p, String n, String pr, String b, String e, String rc, String c) {
            throw new UnsupportedOperationException();
        }

        public List<DataSourceRow> findByProject(String projectId) {
            return List.of();
        }

        public Optional<DataSourceRow> update(String i, String n, String e, String rc, boolean en, long v) {
            throw new UnsupportedOperationException();
        }

        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }

        public List<DataSourceRow> findByProjectPaged(String projectId, String protocol,
                OffsetDateTime afterAt, String afterId, int limit) {
            return List.of();
        }

        public Optional<DataSourceRow> duplicate(String sourceId, String newName, String createdBy) {
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

        /** Returns the {@code trigger} stored for the given run, or {@code null} if not found. */
        public String triggerOf(String runId) {
            RunRow row = byId.get(runId);
            return row == null ? null : row.trigger();
        }

        /** Returns the {@code initiator} stored for the given run, or {@code null} if not found. */
        public String initiatorOf(String runId) {
            RunRow row = byId.get(runId);
            return row == null ? null : row.initiator();
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

    private static final class FakeEvidence implements EvidenceRepository {
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
            throw new UnsupportedOperationException();
        }

        public List<EvidenceRow> findByProject(String projectId) {
            throw new UnsupportedOperationException();
        }

        public EvidenceRow updateStatus(String id, String status, String objectRef) {
            throw new UnsupportedOperationException();
        }

        public List<EvidenceRow> findByProjectPaged(String projectId, OffsetDateTime afterAt,
                String afterId, int limit) {
            return List.of();
        }
    }
}
