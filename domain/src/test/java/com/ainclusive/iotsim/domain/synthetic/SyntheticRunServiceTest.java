package com.ainclusive.iotsim.domain.synthetic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.run.FakeRuntimeEventRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.persistence.timeline.RunValueTimelineRepository;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SyntheticRunServiceTest {

    private static final String PROJECT = "p1";
    private static final String SOURCE = "ds1";
    private static final Clock FIXED = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);

    private final ObjectMapper json = new ObjectMapper();
    private CapturingRuntime runtime;
    private FakeRuns runs;
    private FakeEvidence evidence;
    private FakeRuntimeEventRepository events;
    private FakeRunValueTimeline runValueTimeline;

    @BeforeEach
    void setUp() {
        runtime = new CapturingRuntime();
        runs = new FakeRuns();
        evidence = new FakeEvidence();
        events = new FakeRuntimeEventRepository();
        runValueTimeline = new FakeRunValueTimeline();
    }

    private SyntheticConfig config(Long seed) {
        return new SyntheticConfig(seed, List.of(
                new SyntheticVariableConfig("temp", DataType.FLOAT64,
                        new PatternSpec("SINE", null, 0.0, 10.0, 1000L, null, null, null), 100),
                new SyntheticVariableConfig("rnd", DataType.FLOAT64,
                        new PatternSpec("RANDOM_UNIFORM", null, 0.0, 1.0, null, null, null, null), 250)));
    }

    private SyntheticRunService service(String basis, SyntheticConfig config) {
        String rc = config == null ? "{}" : json.writeValueAsString(config);
        return new SyntheticRunService(new FakeDataSources(basis, rc), new EmptySchemas(),
                runtime, runs, evidence, events, runValueTimeline, json, FIXED);
    }

    @Test
    void appliesBoundedDeterministicSeriesAndOpensRunAndEvidence() {
        SyntheticRunService service = service("SYNTHETIC", config(5L));

        SyntheticRunSummary summary = service.run(PROJECT, SOURCE, 1000);

        // temp: 1000/100 = 10 ticks; rnd: 1000/250 = 4 ticks => 14 values.
        assertThat(summary.valueCount()).isEqualTo(14);
        assertThat(summary.seed()).isEqualTo(5L);
        assertThat(runtime.applied).hasSize(14);
        assertThat(runtime.state(SOURCE)).isEqualTo("RUNNING");
        // IS-185: generated values are persisted for evidence export, keyed by run.
        assertThat(runValueTimeline.readAll(summary.runId())).hasSize(14);

        RunRow run = runs.byId.get(summary.runId());
        assertThat(run.kind()).isEqualTo("SYNTHETIC");
        assertThat(run.state()).isEqualTo("COMPLETED");
        assertThat(run.sourceIds()).containsExactly(SOURCE);
        EvidenceRow ev = evidence.byId.get(summary.evidenceId());
        assertThat(ev.manifestJson()).contains("\"seed\":5");
        assertThat(ev.manifestJson()).contains("\"kind\":\"SYNTHETIC\"");
        assertThat(ev.manifestJson()).contains("\"runId\":\"" + summary.runId() + "\"");
        assertThat(ev.manifestJson()).doesNotContain("\"endedAt\":null");
        // IS-182: run completion is mirrored into runtime_events for the Events tab.
        assertThat(events.appended).extracting("type").containsExactly("RUN_COMPLETED");
        assertThat(events.appended.get(0).runId()).isEqualTo(summary.runId());
        // IS-187: a completed run's evidence leaves CAPTURING for READY.
        assertThat(evidence.byId.get(summary.evidenceId()).status()).isEqualTo("READY");
    }

    @Test
    void runtimeFailureStampsEndedAtOnEvidenceManifest() {
        SyntheticRunService service = new SyntheticRunService(
                new FakeDataSources("SYNTHETIC", json.writeValueAsString(config(1L))),
                new EmptySchemas(), new ThrowingRuntime(), runs, evidence, events, runValueTimeline, json, FIXED);
        assertThatThrownBy(() -> service.run(PROJECT, SOURCE, 1000))
                .isInstanceOf(IllegalStateException.class);
        EvidenceRow ev = evidence.byId.values().iterator().next();
        assertThat(ev.manifestJson()).doesNotContain("\"endedAt\":null");
        // IS-185 review: append happens after applyValues, so a run that never got to
        // apply its values must not have them show up in evidence as if they were sent.
        assertThat(runValueTimeline.readAll(runs.byId.keySet().iterator().next())).isEmpty();
    }

    @Test
    void sameSeedProducesIdenticalSeries() {
        List<NeutralValue> first = service("SYNTHETIC", config(5L)).run(PROJECT, SOURCE, 1000) != null
                ? new ArrayList<>(runtime.applied) : null;
        CapturingRuntime second = new CapturingRuntime();
        SyntheticRunService svc2 = new SyntheticRunService(
                new FakeDataSources("SYNTHETIC", json.writeValueAsString(config(5L))),
                new EmptySchemas(), second, new FakeRuns(), new FakeEvidence(), new FakeRuntimeEventRepository(),
                new FakeRunValueTimeline(), json, FIXED);
        svc2.run(PROJECT, SOURCE, 1000);
        assertThat(second.applied).isEqualTo(first);
    }

    @Test
    void nonSyntheticSourceRejected() {
        assertThatThrownBy(() -> service("MANUAL", config(1L)).run(PROJECT, SOURCE, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not synthetic");
    }

    @Test
    void nonPositiveDurationRejected() {
        assertThatThrownBy(() -> service("SYNTHETIC", config(1L)).run(PROJECT, SOURCE, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("durationMs");
    }

    @Test
    void missingSourceThrowsNotFound() {
        SyntheticRunService service = new SyntheticRunService(new FakeDataSources("SYNTHETIC", "{}"),
                new EmptySchemas(), runtime, runs, evidence, events, runValueTimeline, json, FIXED);
        assertThatThrownBy(() -> service.run(PROJECT, "nope", 1000))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void runtimeFailureEndsRunFailed() {
        SyntheticRunService service = new SyntheticRunService(
                new FakeDataSources("SYNTHETIC", json.writeValueAsString(config(1L))),
                new EmptySchemas(), new ThrowingRuntime(), runs, evidence, events, runValueTimeline, json, FIXED);
        assertThatThrownBy(() -> service.run(PROJECT, SOURCE, 1000))
                .isInstanceOf(IllegalStateException.class);
        assertThat(runs.byId.values()).singleElement()
                .extracting(RunRow::state).isEqualTo("FAILED");
        // IS-182: run failure is mirrored into runtime_events for the Events tab.
        assertThat(events.appended).extracting("type").containsExactly("RUN_FAILED");
        // IS-187: a failed run's evidence leaves CAPTURING for PARTIAL, not READY.
        assertThat(evidence.byId.values()).singleElement()
                .extracting(EvidenceRow::status).isEqualTo("PARTIAL");
    }

    @Test
    void runWithParentRunIdPassesItToRunCreate() {
        SyntheticRunService service = service("SYNTHETIC", config(5L));
        SyntheticRunSummary summary = service.run(PROJECT, SOURCE, 1000L, "parent-run-9");
        assertThat(runs.byId.get(summary.runId()).parentRunId()).isEqualTo("parent-run-9");
    }

    @Test
    void runWithAutomationTriggerAndInitiator() {
        SyntheticRunService service = service("SYNTHETIC", config(5L));
        SyntheticRunSummary summary = service.run(PROJECT, SOURCE, 1000L, "AUTOMATION", "ci-bot", null);
        assertThat(runs.triggerOf(summary.runId())).isEqualTo("AUTOMATION");
        assertThat(runs.initiatorOf(summary.runId())).isEqualTo("ci-bot");
    }

    @Test
    void runDefaultOverloadUsesManualTriggerAndLocalInitiator() {
        SyntheticRunService service = service("SYNTHETIC", config(5L));
        SyntheticRunSummary summary = service.run(PROJECT, SOURCE, 1000L);
        assertThat(runs.triggerOf(summary.runId())).isEqualTo("MANUAL");
        assertThat(runs.initiatorOf(summary.runId())).isEqualTo("local");
    }

    // --- fakes ---

    private static final class CapturingRuntime implements RuntimeController {
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

    private record FakeDataSources(String basis, String runtimeConfig) implements DataSourceRepository {
        public Optional<DataSourceRow> findById(String id) {
            if (!SOURCE.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new DataSourceRow(id, PROJECT, "Gen", "OPC_UA", basis,
                    null, null, 0, null, runtimeConfig, null, false, now, now, "local", 0));
        }

        public DataSourceRow insert(String p, String n, String pr, String b, int sp, String rde,
                String rc, String sc, String c) {
            throw new UnsupportedOperationException();
        }

        public List<DataSourceRow> findByProject(String projectId) {
            return List.of();
        }

        public Optional<DataSourceRow> update(String i, String n, int sp, String rde, String rc,
                String sc, boolean en, long v) {
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

    private static final class FakeRunValueTimeline implements RunValueTimelineRepository {
        final java.util.Map<String, List<NeutralValue>> byRun = new java.util.LinkedHashMap<>();

        public long append(String runId, List<NeutralValue> values) {
            byRun.computeIfAbsent(runId, k -> new ArrayList<>()).addAll(values);
            return values.size();
        }

        public List<NeutralValue> readAll(String runId) {
            return byRun.getOrDefault(runId, List.of());
        }

        public void deleteByRun(String runId) {
            byRun.remove(runId);
        }
    }
}
