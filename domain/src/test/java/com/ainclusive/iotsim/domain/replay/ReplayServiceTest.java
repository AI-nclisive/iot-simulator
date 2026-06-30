package com.ainclusive.iotsim.domain.replay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
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
import com.ainclusive.iotsim.platform.runtime.InMemoryRuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ReplayServiceTest {

    private static final String PROJECT = "p1";
    private static final String SOURCE = "ds1";
    private static final String RECORDING = "rec1";

    private InMemoryRuntimeController runtime;
    private FakeRuns runs;
    private FakeEvidence evidence;
    private ReplayService service;

    @BeforeEach
    void setUp() {
        runtime = new InMemoryRuntimeController();
        runs = new FakeRuns();
        evidence = new FakeEvidence();
        service = build(runtime);
    }

    private ReplayService build(RuntimeController controller) {
        List<NeutralValue> values = List.of(
                NeutralValue.good("temp", Instant.parse("2026-01-01T00:00:00Z"), 1.0),
                NeutralValue.good("temp", Instant.parse("2026-01-01T00:00:01Z"), 2.0),
                NeutralValue.good("temp", Instant.parse("2026-01-01T00:00:02Z"), 3.0));
        return new ReplayService(
                new FakeDataSources(SOURCE, PROJECT),
                new FakeRecordings(RECORDING, PROJECT),
                new FakeTimeline(values),
                new EmptySchemas(),
                controller,
                runs,
                evidence,
                new ObjectMapper());
    }

    @Test
    void replayStartsSourceAndStreamsAllValues() {
        ReplaySummary summary = service.replay(PROJECT, SOURCE, RECORDING);
        assertThat(summary.valueCount()).isEqualTo(3);
        assertThat(runtime.state(SOURCE)).isEqualTo("RUNNING");
        assertThat(runtime.appliedCount(SOURCE)).isEqualTo(3);
    }

    @Test
    void replayOpensRunAndEvidenceAndReturnsTheirIds() {
        ReplaySummary summary = service.replay(PROJECT, SOURCE, RECORDING);

        assertThat(summary.runId()).isNotBlank();
        assertThat(summary.evidenceId()).isNotBlank();

        RunRow run = runs.byId.get(summary.runId());
        assertThat(run.kind()).isEqualTo("REPLAY");
        assertThat(run.trigger()).isEqualTo("MANUAL");
        assertThat(run.sourceIds()).containsExactly(SOURCE);
        assertThat(run.state()).isEqualTo("COMPLETED");
        assertThat(run.startedAt()).isNotNull();
        assertThat(run.endedAt()).isNotNull();
        assertThat(run.evidenceId()).isEqualTo(summary.evidenceId());

        EvidenceRow ev = evidence.byId.get(summary.evidenceId());
        assertThat(ev.runId()).isEqualTo(summary.runId());
        // recordingId is seeded so export assembly can find the replayed timeline.
        assertThat(ev.manifestJson()).contains("\"recordingId\":\"" + RECORDING + "\"");
    }

    @Test
    void replayFailureEndsRunFailedAndPropagates() {
        ReplayService failing = build(new ThrowingRuntime());

        assertThatThrownBy(() -> failing.replay(PROJECT, SOURCE, RECORDING))
                .isInstanceOf(IllegalStateException.class);

        assertThat(runs.byId.values()).singleElement()
                .extracting(RunRow::state).isEqualTo("FAILED");
    }

    @Test
    void evidenceSetupFailureEndsRunFailedNotOrphaned() {
        evidence.failCreate = true;

        assertThatThrownBy(() -> service.replay(PROJECT, SOURCE, RECORDING))
                .isInstanceOf(IllegalStateException.class);

        // The run was opened, so it must reach a terminal state rather than be left RUNNING.
        assertThat(runs.byId.values()).singleElement()
                .extracting(RunRow::state).isEqualTo("FAILED");
    }

    @Test
    void replayMissingRecordingThrowsNotFoundWithoutOpeningARun() {
        assertThatThrownBy(() -> service.replay(PROJECT, SOURCE, "nope"))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(runs.byId).isEmpty();
        assertThat(evidence.byId).isEmpty();
    }

    @Test
    void replayMissingSourceThrowsNotFound() {
        assertThatThrownBy(() -> service.replay(PROJECT, "nope", RECORDING))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // --- fakes ---

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

        public com.ainclusive.iotsim.platform.runtime.SourceHealth health(String id) {
            return new com.ainclusive.iotsim.platform.runtime.SourceHealth("STOPPED", null);
        }
    }

    private static final class FakeRuns implements RunRepository {
        final java.util.Map<String, RunRow> byId = new java.util.LinkedHashMap<>();
        private int seq;

        public RunRow create(String projectId, String kind, String trigger, String initiator,
                List<String> sourceIds, String scenarioId) {
            String id = "run-" + (++seq);
            RunRow row = new RunRow(id, projectId, kind, trigger, initiator, "QUEUED",
                    scenarioId, null, null, null, OffsetDateTime.now(ZoneOffset.UTC),
                    new ArrayList<>(sourceIds));
            byId.put(id, row);
            return row;
        }

        public RunRow start(String id, OffsetDateTime startedAt) {
            RunRow r = byId.get(id);
            RunRow updated = new RunRow(r.id(), r.projectId(), r.kind(), r.trigger(), r.initiator(),
                    "RUNNING", r.scenarioId(), r.evidenceId(), startedAt, r.endedAt(), r.createdAt(),
                    r.sourceIds());
            byId.put(id, updated);
            return updated;
        }

        public RunRow end(String id, String terminalState, OffsetDateTime endedAt) {
            RunRow r = byId.get(id);
            RunRow updated = new RunRow(r.id(), r.projectId(), r.kind(), r.trigger(), r.initiator(),
                    terminalState, r.scenarioId(), r.evidenceId(), r.startedAt(), endedAt, r.createdAt(),
                    r.sourceIds());
            byId.put(id, updated);
            return updated;
        }

        public RunRow linkEvidence(String runId, String evidenceId) {
            RunRow r = byId.get(runId);
            RunRow updated = new RunRow(r.id(), r.projectId(), r.kind(), r.trigger(), r.initiator(),
                    r.state(), r.scenarioId(), evidenceId, r.startedAt(), r.endedAt(), r.createdAt(),
                    r.sourceIds());
            byId.put(runId, updated);
            return updated;
        }

        public Optional<RunRow> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        public List<RunRow> findByProject(String projectId) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeEvidence implements EvidenceRepository {
        final java.util.Map<String, EvidenceRow> byId = new java.util.LinkedHashMap<>();
        boolean failCreate;
        private int seq;

        public EvidenceRow create(String projectId, String runId, String createdBy) {
            if (failCreate) {
                throw new IllegalStateException("evidence store unavailable");
            }
            String id = "ev-" + (++seq);
            EvidenceRow row = new EvidenceRow(id, projectId, runId, "CAPTURING", "{}", null,
                    OffsetDateTime.now(ZoneOffset.UTC), createdBy);
            byId.put(id, row);
            return row;
        }

        public EvidenceRow updateManifest(String id, String manifestJson) {
            EvidenceRow e = byId.get(id);
            EvidenceRow updated = new EvidenceRow(e.id(), e.projectId(), e.runId(), e.status(),
                    manifestJson, e.objectRef(), e.createdAt(), e.createdBy());
            byId.put(id, updated);
            return updated;
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
    }

    private record FakeDataSources(String id, String projectId) implements DataSourceRepository {
        @Override
        public Optional<DataSourceRow> findById(String id) {
            if (!this.id.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new DataSourceRow(id, projectId, "src", "OPC_UA", "MANUAL",
                    null, null, "{}", "{}", false, now, now, "local", 0));
        }

        @Override
        public DataSourceRow insert(String p, String n, String pr, String b, String e, String rc, String c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DataSourceRow> findByProject(String projectId) {
            return List.of();
        }

        @Override
        public Optional<DataSourceRow> update(String i, String n, String e, String rc, boolean en, long v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }
    }

    private record FakeRecordings(String id, String projectId) implements RecordingRepository {
        @Override
        public Optional<RecordingRow> findById(String id) {
            if (!this.id.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new RecordingRow(id, projectId, "ds1", 1, "SCAN_RECORD",
                    null, null, 0, 0, now, now, "local", 0));
        }

        @Override
        public RecordingRow create(String p, String d, int sv, String o, String c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RecordingRow> findByProject(String projectId) {
            return List.of();
        }

        @Override
        public RecordingRow finalizeStats(String i, OffsetDateTime s, OffsetDateTime e, long c, long b) {
            throw new UnsupportedOperationException();
        }
    }

    private record FakeTimeline(List<NeutralValue> values) implements ValueTimelineRepository {
        @Override
        public List<NeutralValue> readAll(String recordingId) {
            return values;
        }

        @Override
        public long append(String recordingId, List<NeutralValue> values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<NeutralValue> readRange(String recordingId, Instant from, Instant to) {
            return values;
        }

        @Override
        public long count(String recordingId) {
            return values.size();
        }
    }

    private record EmptySchemas() implements SchemaRepository {
        @Override
        public Optional<SchemaWithNodes> findCurrent(String dataSourceId) {
            return Optional.empty();
        }

        @Override
        public SchemaWithNodes saveNewVersion(String dataSourceId, List<SchemaNode> nodes) {
            throw new UnsupportedOperationException();
        }
    }
}
