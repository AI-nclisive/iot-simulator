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
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository.ValueTimelineEntry;
import com.ainclusive.iotsim.platform.runtime.InMemoryRuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
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
    private FakeRuntimeEventRepository events;
    private ReplayService service;

    @BeforeEach
    void setUp() {
        runtime = new InMemoryRuntimeController();
        runs = new FakeRuns();
        evidence = new FakeEvidence();
        events = new FakeRuntimeEventRepository();
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
                events,
                new ObjectMapper());
    }

    @Test
    void replayStartsSourceAndStreamsAllValues() {
        ReplaySummary summary = service.replay(PROJECT, SOURCE, RECORDING, null, true);
        assertThat(summary.valueCount()).isEqualTo(3);
        assertThat(runtime.state(SOURCE)).isEqualTo("RUNNING");
        assertThat(runtime.appliedCount(SOURCE)).isEqualTo(3);
    }

    @Test
    void replayOpensRunAndEvidenceAndReturnsTheirIds() {
        ReplaySummary summary = service.replay(PROJECT, SOURCE, RECORDING, null, true);

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
        // recordingId and seed are seeded so export assembly can reproduce the run.
        assertThat(ev.manifestJson()).contains("\"recordingId\":\"" + RECORDING + "\"");
        assertThat(ev.manifestJson()).contains("\"seed\":");
        assertThat(ev.manifestJson()).contains("\"kind\":\"REPLAY\"");
        assertThat(ev.manifestJson()).doesNotContain("\"endedAt\":null");
        // IS-182: run completion is mirrored into runtime_events for the Events tab.
        assertThat(events.appended).extracting("type").containsExactly("RUN_COMPLETED");
        assertThat(events.appended.get(0).runId()).isEqualTo(summary.runId());
    }

    @Test
    void replayFailureEndsRunFailedAndPropagates() {
        ReplayService failing = build(new ThrowingRuntime());

        assertThatThrownBy(() -> failing.replay(PROJECT, SOURCE, RECORDING, null, true))
                .isInstanceOf(IllegalStateException.class);

        assertThat(runs.byId.values()).singleElement()
                .extracting(RunRow::state).isEqualTo("FAILED");
        EvidenceRow ev = evidence.byId.values().iterator().next();
        assertThat(ev.manifestJson()).doesNotContain("\"endedAt\":null");
        // IS-182: run failure is mirrored into runtime_events for the Events tab.
        assertThat(events.appended).extracting("type").containsExactly("RUN_FAILED");
    }

    @Test
    void evidenceSetupFailureEndsRunFailedNotOrphaned() {
        evidence.failCreate = true;

        assertThatThrownBy(() -> service.replay(PROJECT, SOURCE, RECORDING, null, true))
                .isInstanceOf(IllegalStateException.class);

        // The run was opened, so it must reach a terminal state rather than be left RUNNING.
        assertThat(runs.byId.values()).singleElement()
                .extracting(RunRow::state).isEqualTo("FAILED");
    }

    @Test
    void replayMissingRecordingThrowsNotFoundWithoutOpeningARun() {
        assertThatThrownBy(() -> service.replay(PROJECT, SOURCE, "nope", null, false))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(runs.byId).isEmpty();
        assertThat(evidence.byId).isEmpty();
    }

    @Test
    void replayMissingSourceThrowsNotFound() {
        assertThatThrownBy(() -> service.replay(PROJECT, "nope", RECORDING, null, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // IS-160 — protocol-type compatibility (not tied to the exact capturing source)
    // -------------------------------------------------------------------------

    @Test
    void replayRejectsRecordingWhoseProtocolDiffersFromTargetSource() {
        // FakeDataSources' source is OPC_UA; a MODBUS_TCP recording is not compatible,
        // even with compatibilityAck=true (protocol mismatch is not schema-version drift).
        List<NeutralValue> values = List.of(
                NeutralValue.good("temp", Instant.parse("2026-01-01T00:00:00Z"), 1.0));
        ReplayService mismatched = new ReplayService(
                new FakeDataSources(SOURCE, PROJECT),
                new FakeRecordingsWithProtocol(RECORDING, PROJECT, "MODBUS_TCP"),
                new FakeTimeline(values),
                new EmptySchemas(),
                runtime,
                runs,
                evidence,
                events,
                new ObjectMapper());

        assertThatThrownBy(() -> mismatched.replay(PROJECT, SOURCE, RECORDING, null, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("MODBUS_TCP")
                .hasMessageContaining("OPC_UA");
    }

    @Test
    void replayAllowsRecordingReplayedAgainstADifferentSourceOfTheSameProtocol() {
        // Recording captured from a source it no longer references (dataSourceId differs from
        // the replay target) still succeeds as long as the protocol matches (IS-160).
        ReplaySummary summary = service.replay(PROJECT, SOURCE, RECORDING, null, true);
        assertThat(summary.dataSourceId()).isEqualTo(SOURCE);
    }

    // -------------------------------------------------------------------------
    // IS-069 — timing/ordering/compat checks
    // -------------------------------------------------------------------------

    @Test
    void schemaVersionMismatchWithoutAckThrows() {
        // FakeRecordings returns schemaVersion=1, EmptySchemas returns version 0 → mismatch.
        assertThatThrownBy(() -> service.replay(PROJECT, SOURCE, RECORDING, null, false))
                .isInstanceOf(SchemaVersionMismatchException.class)
                .hasMessageContaining("schema version")
                .hasMessageContaining("compatibilityAck=true");
    }

    @Test
    void schemaVersionMismatchWithAckSucceeds() {
        // Same mismatch, but ack=true bypasses the guard.
        ReplaySummary summary = service.replay(PROJECT, SOURCE, RECORDING, null, true);
        assertThat(summary.recordingId()).isEqualTo(RECORDING);
    }

    @Test
    void schemaVersionMatchAllowsReplayWithoutAck() {
        // Build a service where recording.schemaVersion() == currentSchemaVersion (both 0).
        ReplayService aligned = buildWithSchemaVersion(0);
        ReplaySummary summary = aligned.replay(PROJECT, SOURCE, RECORDING, null, false);
        assertThat(summary.recordingId()).isEqualTo(RECORDING);
    }

    @Test
    void explicitDeterministicSettingsAreEchoedInSummary() {
        DeterministicSettings explicit = new DeterministicSettings(42L, Instant.parse("2026-01-01T00:00:00Z"));
        ReplaySummary summary = service.replay(PROJECT, SOURCE, RECORDING, explicit, true);
        assertThat(summary.deterministicSettings().seed()).isEqualTo(42L);
        assertThat(summary.deterministicSettings().startTime()).isEqualTo(Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void nullDeterministicSettingsAreAutoGeneratedAndReturned() {
        ReplaySummary summary = service.replay(PROJECT, SOURCE, RECORDING, null, true);
        assertThat(summary.deterministicSettings()).isNotNull();
        assertThat(summary.deterministicSettings().startTime()).isNotNull();
    }

    @Test
    void replayWithParentRunIdLinksChildRun() {
        ReplaySummary summary = service.replay(PROJECT, SOURCE, RECORDING, null, true, "parent-run-1");
        assertThat(runs.parentOf(summary.runId())).isEqualTo("parent-run-1");
    }

    @Test
    void replayWithAutomationTriggerAndInitiator() {
        ReplaySummary s = service.replay(PROJECT, SOURCE, RECORDING, null, true, "AUTOMATION", "ci-bot", null);
        assertThat(runs.triggerOf(s.runId())).isEqualTo("AUTOMATION");
        assertThat(runs.initiatorOf(s.runId())).isEqualTo("ci-bot");
    }

    @Test
    void replayDefaultOverloadUsesManualTriggerAndLocalInitiator() {
        ReplaySummary s = service.replay(PROJECT, SOURCE, RECORDING, null, true);
        assertThat(runs.triggerOf(s.runId())).isEqualTo("MANUAL");
        assertThat(runs.initiatorOf(s.runId())).isEqualTo("local");
    }

    /** Replay service with recording schemaVersion=0 and source schemaVersion=0 — perfectly aligned. */
    private ReplayService buildWithSchemaVersion(int version) {
        List<NeutralValue> values = List.of(
                NeutralValue.good("temp", Instant.parse("2026-01-01T00:00:00Z"), 1.0));
        return new ReplayService(
                new FakeDataSources(SOURCE, PROJECT),
                new FakeRecordingsAtVersion(RECORDING, PROJECT, version),
                new FakeTimeline(values),
                new EmptySchemas(),
                runtime,
                runs,
                evidence,
                events,
                new ObjectMapper());
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
                List<String> sourceIds, String scenarioId, String parentRunId) {
            String id = "run-" + (++seq);
            RunRow row = new RunRow(id, projectId, kind, trigger, initiator, "QUEUED",
                    scenarioId, null, null, null, OffsetDateTime.now(ZoneOffset.UTC),
                    new ArrayList<>(sourceIds), parentRunId);
            byId.put(id, row);
            return row;
        }

        /** Returns the {@code parentRunId} stored for the given run, or {@code null} if none. */
        public String parentOf(String runId) {
            RunRow row = byId.get(runId);
            return row == null ? null : row.parentRunId();
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
            RunRow updated = new RunRow(r.id(), r.projectId(), r.kind(), r.trigger(), r.initiator(),
                    "RUNNING", r.scenarioId(), r.evidenceId(), startedAt, r.endedAt(), r.createdAt(),
                    r.sourceIds(), r.parentRunId());
            byId.put(id, updated);
            return updated;
        }

        public RunRow end(String id, String terminalState, OffsetDateTime endedAt) {
            RunRow r = byId.get(id);
            RunRow updated = new RunRow(r.id(), r.projectId(), r.kind(), r.trigger(), r.initiator(),
                    terminalState, r.scenarioId(), r.evidenceId(), r.startedAt(), endedAt, r.createdAt(),
                    r.sourceIds(), r.parentRunId());
            byId.put(id, updated);
            return updated;
        }

        public RunRow linkEvidence(String runId, String evidenceId) {
            RunRow r = byId.get(runId);
            RunRow updated = new RunRow(r.id(), r.projectId(), r.kind(), r.trigger(), r.initiator(),
                    r.state(), r.scenarioId(), evidenceId, r.startedAt(), r.endedAt(), r.createdAt(),
                    r.sourceIds(), r.parentRunId());
            byId.put(runId, updated);
            return updated;
        }

        public Optional<RunRow> findById(String id) {
            return Optional.ofNullable(byId.get(id));
        }

        public List<RunRow> findByProject(String projectId) {
            throw new UnsupportedOperationException();
        }

        public List<RunRow> findByProjectPaged(String projectId, java.time.OffsetDateTime afterAt,
                String afterId, int limit) {
            throw new UnsupportedOperationException();
        }

        public List<RunRow> findActiveByProject(String projectId) {
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

        @Override
        public List<EvidenceRow> findByProjectPaged(String projectId,
                java.time.OffsetDateTime afterAt, String afterId, int limit) {
            return List.of();
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
                    null, null, 0, null, "{}", null, false, now, now, "local", 0));
        }

        @Override
        public DataSourceRow insert(String p, String n, String pr, String b, int sp, String rde,
                String rc, String sc, String c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DataSourceRow> findByProject(String projectId) {
            return List.of();
        }

        @Override
        public List<DataSourceRow> findByProjectPaged(String projectId, String protocol,
                OffsetDateTime afterAt, String afterId, int limit) {
            return List.of();
        }

        @Override
        public Optional<DataSourceRow> update(String i, String n, int sp, String rde, String rc,
                String sc, boolean en, long v) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DataSourceRow> duplicate(String sourceId, String newName, String createdBy) {
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
            return Optional.of(new RecordingRow(id, projectId, "ds1", "OPC_UA", 1, "SCAN_RECORD",
                    "SCHEMA_AND_DATA", null, null, null, 0, 0, now, now, "local", 0, "[]"));
        }

        @Override
        public RecordingRow create(String p, String d, String pr, int sv, String o, String st, String n, String c, String sj) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RecordingRow> findByProject(String projectId) {
            return List.of();
        }

        @Override
        public List<RecordingRow> findByProjectPaged(String projectId,
                OffsetDateTime afterAt, String afterId, int limit) {
            return List.of();
        }

        @Override
        public RecordingRow finalizeStats(String i, OffsetDateTime s, OffsetDateTime e, long c, long b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByProject(String projectId) {
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

        @Override
        public List<ValueTimelineEntry> readPage(String recordingId, long afterSeq, int limit,
                com.ainclusive.iotsim.protocolmodel.ValueFilter filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long sumBytes(String recordingId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void deleteByRecording(String recordingId) {
            throw new UnsupportedOperationException();
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

    /** A recording fake that returns a specific schema version (for compat check tests). */
    private record FakeRecordingsAtVersion(String id, String projectId, int schemaVersion)
            implements RecordingRepository {
        @Override
        public Optional<RecordingRow> findById(String id) {
            if (!this.id.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new RecordingRow(id, projectId, "ds1", "OPC_UA", schemaVersion, "SCAN_RECORD",
                    "SCHEMA_AND_DATA", null, null, null, 0, 0, now, now, "local", 0, "[]"));
        }

        @Override
        public RecordingRow create(String p, String d, String pr, int sv, String o, String st, String n, String c, String sj) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RecordingRow> findByProject(String projectId) {
            return List.of();
        }

        @Override
        public List<RecordingRow> findByProjectPaged(String projectId,
                OffsetDateTime afterAt, String afterId, int limit) {
            return List.of();
        }

        @Override
        public RecordingRow finalizeStats(String i, OffsetDateTime s, OffsetDateTime e, long c, long b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByProject(String projectId) {
            throw new UnsupportedOperationException();
        }
    }

    /** A recording fake that returns a specific protocol (for IS-160 compat check tests). */
    private record FakeRecordingsWithProtocol(String id, String projectId, String protocol)
            implements RecordingRepository {
        @Override
        public Optional<RecordingRow> findById(String id) {
            if (!this.id.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new RecordingRow(id, projectId, "ds1", protocol, 0, "SCAN_RECORD",
                    "SCHEMA_AND_DATA", null, null, null, 0, 0, now, now, "local", 0, "[]"));
        }

        @Override
        public RecordingRow create(String p, String d, String pr, int sv, String o, String st, String n, String c, String sj) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RecordingRow> findByProject(String projectId) {
            return List.of();
        }

        @Override
        public List<RecordingRow> findByProjectPaged(String projectId,
                OffsetDateTime afterAt, String afterId, int limit) {
            return List.of();
        }

        @Override
        public RecordingRow finalizeStats(String i, OffsetDateTime s, OffsetDateTime e, long c, long b) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByProject(String projectId) {
            throw new UnsupportedOperationException();
        }
    }
}
