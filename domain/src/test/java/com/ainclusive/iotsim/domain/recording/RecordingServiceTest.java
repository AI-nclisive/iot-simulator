package com.ainclusive.iotsim.domain.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.activityevent.ActivityEventService;
import com.ainclusive.iotsim.domain.activityevent.NoOpActivityEventRepository;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.common.RetentionDependencyException;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.project.ProjectRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepInput;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository.ValueTimelineEntry;
import com.ainclusive.iotsim.platform.capture.CaptureException;
import com.ainclusive.iotsim.platform.capture.CaptureSession;
import com.ainclusive.iotsim.platform.capture.CaptureSpec;
import com.ainclusive.iotsim.platform.capture.SourceCapturer;
import com.ainclusive.iotsim.platform.secret.InMemoryCredentialStore;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class RecordingServiceTest {

    private static final String PROJECT = "proj-1";
    private static final String SOURCE = "ds-1";
    private static final String ENDPOINT = "opc.tcp://real:4840/iotsim";

    private RecordingService service;
    private FakeDataSourceRepository sources;
    private FakeSchemaRepository schemas;
    private FakeCapturer capturer;
    private InMemoryScenarioRepository scenarios;
    private InMemoryRunRepository runs;
    private InMemoryEvidenceRepository evidence;
    private RecordingRepository recordingsUsedByService;

    @BeforeEach
    void setUp() {
        sources = new FakeDataSourceRepository(SOURCE, PROJECT, ENDPOINT);
        schemas = new FakeSchemaRepository();
        capturer = new FakeCapturer();
        scenarios = new InMemoryScenarioRepository();
        runs = new InMemoryRunRepository();
        evidence = new InMemoryEvidenceRepository();
        recordingsUsedByService = new InMemoryRecordingRepository();
        service = new RecordingService(
                recordingsUsedByService,
                new InMemoryValueTimelineRepository(),
                sources,
                schemas,
                new InMemoryCredentialStore(),
                capturer,
                fakeProjects(),
                new ActivityEventService(new NoOpActivityEventRepository()),
                scenarios,
                runs,
                evidence,
                new ObjectMapper());
    }

    @Test
    void createUnderExistingSource() {
        Recording r = service.create(PROJECT, SOURCE, com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "alice");
        assertThat(r.origin()).isEqualTo("SCAN_RECORD");
        assertThat(r.valueCount()).isZero();
        assertThat(r.dataSourceId()).isEqualTo(SOURCE);
    }

    @Test
    void createUnderMissingSourceThrowsNotFound() {
        assertThatThrownBy(() -> service.create(PROJECT, "nope",
                        com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "a"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void appendValuesIsNoOpForSchemaOnlyRecording() {
        Recording r = service.create(PROJECT, SOURCE, com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_ONLY, null, "a");
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        long written = service.appendValues(PROJECT, r.id(), List.of(
                NeutralValue.good("v1", t, 1.0)));
        assertThat(written).isZero();
        Recording completed = service.complete(PROJECT, r.id());
        assertThat(completed.valueCount()).isZero();
    }

    @Test
    void appendThenCompleteUpdatesValueCount() {
        Recording r = service.create(PROJECT, SOURCE, com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "a");
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        long written = service.appendValues(PROJECT, r.id(), List.of(
                NeutralValue.good("v1", t, 1.0),
                NeutralValue.good("v1", t.plusMillis(1), 2.0),
                NeutralValue.good("v1", t.plusMillis(2), 3.0)));
        assertThat(written).isEqualTo(3);

        Recording completed = service.complete(PROJECT, r.id());
        assertThat(completed.valueCount()).isEqualTo(3);
    }

    @Test
    void getMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.get(PROJECT, "nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void startCaptureCreatesRecordingAndStreamsValuesUntilStop() {
        schemas.set(2, List.of(variable("temp", DataType.FLOAT64)));

        Recording started = service.startCapture(PROJECT, SOURCE, "alice");
        assertThat(started.origin()).isEqualTo("SCAN_RECORD");
        assertThat(started.schemaVersion()).isEqualTo(2);
        // The capturer was driven against the real endpoint with the source schema.
        assertThat(capturer.spec.endpointUrl()).isEqualTo(ENDPOINT);
        assertThat(capturer.spec.schemaVersion()).isEqualTo(2);

        // A live value batch arriving from the source is appended to the recording.
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        capturer.emit(List.of(
                NeutralValue.good("temp", t, 21.5),
                NeutralValue.good("temp", t.plusSeconds(1), 22.0)));

        Recording stopped = service.stopCapture(PROJECT, SOURCE);
        assertThat(stopped.id()).isEqualTo(started.id());
        assertThat(stopped.valueCount()).isEqualTo(2);
        assertThat(capturer.stopped).isTrue();
    }

    @Test
    void startCaptureWhileAlreadyCapturingIsRejected() {
        schemas.set(1, List.of(variable("temp", DataType.FLOAT64)));
        service.startCapture(PROJECT, SOURCE, "alice");

        assertThatThrownBy(() -> service.startCapture(PROJECT, SOURCE, "bob"))
                .isInstanceOf(CaptureException.class)
                .hasMessageContaining("already running");
    }

    @Test
    void startCaptureWithoutSchemaIsRejected() {
        schemas.clear();
        assertThatThrownBy(() -> service.startCapture(PROJECT, SOURCE, "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no schema");
    }

    @Test
    void startCaptureWithoutEndpointIsRejected() {
        sources.realDeviceEndpoint = null;
        schemas.set(1, List.of(variable("temp", DataType.FLOAT64)));
        assertThatThrownBy(() -> service.startCapture(PROJECT, SOURCE, "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void stopCaptureWithoutActiveCaptureIsRejected() {
        assertThatThrownBy(() -> service.stopCapture(PROJECT, SOURCE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("no active capture");
    }

    @Test
    void listPagedThrowsNotFoundForMissingProject() {
        assertThatThrownBy(() -> service.listPaged("no-such-project", null, null))
                .isInstanceOf(com.ainclusive.iotsim.domain.common.ResourceNotFoundException.class);
    }

    @Test
    void listPagedEmitsCursorWhenResultsExceedLimit() {
        service.create(PROJECT, SOURCE, com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "alice");
        service.create(PROJECT, SOURCE, com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "bob");
        service.create(PROJECT, SOURCE, com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "carol");

        // fakeProjects returns empty → requireProject throws; rebuild with a present project repo
        ProjectRepository existingProject = new ProjectRepository() {
            @Override public Optional<ProjectRow> findById(String id) {
                return PROJECT.equals(id)
                        ? Optional.of(new ProjectRow(id, "n", null, "ACTIVE",
                                OffsetDateTime.now(ZoneOffset.UTC), null, "it", 0))
                        : Optional.empty();
            }
            @Override public ProjectRow insert(String n, String d, String c) { throw new UnsupportedOperationException(); }
            @Override public List<ProjectRow> findAll() { return List.of(); }
            @Override public List<ProjectRow> findAllPaged(String s, OffsetDateTime a, String i, int l) { return List.of(); }
            @Override public Optional<ProjectRow> update(String id, String n, String d, long v) { throw new UnsupportedOperationException(); }
            @Override public Optional<ProjectRow> archive(String id) { throw new UnsupportedOperationException(); }
            @Override public boolean deleteById(String id) { throw new UnsupportedOperationException(); }
        };
        // share the same InMemoryRecordingRepository used by `service`
        InMemoryRecordingRepository recRepo = new InMemoryRecordingRepository();
        RecordingService svc = new RecordingService(recRepo,
                new InMemoryValueTimelineRepository(), sources, schemas,
                new com.ainclusive.iotsim.platform.secret.InMemoryCredentialStore(),
                capturer, existingProject,
                new ActivityEventService(new NoOpActivityEventRepository()),
                new InMemoryScenarioRepository(),
                new InMemoryRunRepository(),
                new InMemoryEvidenceRepository(),
                new ObjectMapper());
        svc.create(PROJECT, SOURCE, com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "alice");
        svc.create(PROJECT, SOURCE, com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "bob");
        svc.create(PROJECT, SOURCE, com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "carol");

        com.ainclusive.iotsim.domain.support.Page<Recording> page = svc.listPaged(PROJECT, null, 2);
        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNotNull();

        com.ainclusive.iotsim.domain.support.Page<Recording> page2 = svc.listPaged(PROJECT, page.nextCursor(), 2);
        assertThat(page2.items()).hasSize(1);
        assertThat(page2.nextCursor()).isNull();
    }

    @Test
    void listValuesReturnsFirstPage() {
        RecordingService svc = service;
        String rid = svc.create(PROJECT, SOURCE, com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "test").id();
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        List<NeutralValue> vals = List.of(
                NeutralValue.good("n1", t, 1.0),
                NeutralValue.good("n2", t.plusSeconds(1), 2.0),
                NeutralValue.good("n3", t.plusSeconds(2), 3.0));
        svc.appendValues(PROJECT, rid, vals);

        var filter = com.ainclusive.iotsim.protocolmodel.ValueFilter.NONE;
        var page = svc.listValues(PROJECT, rid, null, 2, filter);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).parameterId()).isEqualTo("n1");
        assertThat(page.nextCursor()).isNotNull();

        var page2 = svc.listValues(PROJECT, rid, page.nextCursor(), 2, filter);
        assertThat(page2.items()).hasSize(1);
        assertThat(page2.items().get(0).parameterId()).isEqualTo("n3");
        assertThat(page2.nextCursor()).isNull();
    }

    @Test
    void listValuesThrowsForMissingRecording() {
        assertThatThrownBy(() -> service.listValues(PROJECT, "no-such-id", null, null,
                        com.ainclusive.iotsim.protocolmodel.ValueFilter.NONE))
                .isInstanceOf(com.ainclusive.iotsim.domain.common.ResourceNotFoundException.class);
    }

    @Test
    void getRecordingSchemaReturnsNodes() {
        List<SchemaNode> nodes = List.of(variable("temp", DataType.FLOAT64));
        // schemaVersion = 2 (matches FakeDataSourceRepository default); create() snapshots
        // the *current* schema onto the recording row (IS-161), so stub findCurrent here.
        schemas.set(2, nodes);
        Recording r = service.create(PROJECT, SOURCE, com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "alice");

        RecordingService.RecordingSchema schema = service.getRecordingSchema(PROJECT, r.id());
        assertThat(schema.nodes()).hasSize(1);
        assertThat(schema.nodes().get(0).nodeId()).isEqualTo("temp");
    }

    @Test
    void getRecordingSchemaThrowsWhenSchemaAbsent() {
        Recording r = service.create(PROJECT, SOURCE, com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "alice");
        assertThatThrownBy(() -> service.getRecordingSchema(PROJECT, r.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createSnapshotsCurrentSchemaOntoRowAndServesItWithoutLiveLookup() {
        List<SchemaNode> nodes = List.of(variable("temp", DataType.FLOAT64));
        schemas.set(2, nodes);
        Recording r = service.create(PROJECT, SOURCE, com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "alice");

        // The row created carries a non-empty schema snapshot of its own.
        String schemaNodesJson = ((InMemoryRecordingRepository) serviceRecordings())
                .rows.get(r.id()).schemaNodesJson();
        assertThat(schemaNodesJson).isNotBlank();
        assertThat(schemaNodesJson).isNotEqualTo("[]");

        // getRecordingSchema is served from that snapshot: it must not need a live
        // findByVersion lookup (the schema source may no longer resolve at all, IS-161).
        RecordingService.RecordingSchema schema = service.getRecordingSchema(PROJECT, r.id());
        assertThat(schema.nodes()).hasSize(1);
        assertThat(schema.nodes().get(0).nodeId()).isEqualTo("temp");
        assertThat(schemas.findByVersionCalls).isZero();
    }

    @Test
    void startCaptureSnapshotsCurrentSchemaOntoRow() {
        schemas.set(3, List.of(variable("temp", DataType.FLOAT64)));

        Recording started = service.startCapture(PROJECT, SOURCE, "alice");

        String schemaNodesJson = ((InMemoryRecordingRepository) serviceRecordings())
                .rows.get(started.id()).schemaNodesJson();
        assertThat(schemaNodesJson).isNotBlank();
        assertThat(schemaNodesJson).isNotEqualTo("[]");

        RecordingService.RecordingSchema schema = service.getRecordingSchema(PROJECT, started.id());
        assertThat(schema.nodes()).hasSize(1);
        assertThat(schemas.findByVersionCalls).isZero();
    }

    @Test
    void getRecordingSchemaThrowsWhenStoredSnapshotIsEmpty() {
        // No schema stubbed at all: create() stores "[]" since findCurrent() is empty.
        Recording r = service.create(PROJECT, SOURCE, com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "alice");
        String schemaNodesJson = ((InMemoryRecordingRepository) serviceRecordings())
                .rows.get(r.id()).schemaNodesJson();
        assertThat(schemaNodesJson).isEqualTo("[]");

        assertThatThrownBy(() -> service.getRecordingSchema(PROJECT, r.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private RecordingRepository serviceRecordings() {
        return recordingsUsedByService;
    }


    @Test
    void createWithNameStoresAndReturnsTrimmedName() {
        Recording r = service.create(PROJECT, SOURCE,
                com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_ONLY, "  My Recording  ", "bob");
        assertThat(r.name()).isEqualTo("My Recording");
    }

    @Test
    void createWithNullNameReturnsNullName() {
        Recording r = service.create(PROJECT, SOURCE,
                com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_ONLY, null, "bob");
        assertThat(r.name()).isNull();
    }

    @Test
    void createWithBlankNameReturnsNullName() {
        Recording r = service.create(PROJECT, SOURCE,
                com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_ONLY, "   ", "bob");
        assertThat(r.name()).isNull();
    }

    @Test
    void createWithNameExceeding255CharsThrowsIllegalArgument() {
        String longName = "a".repeat(256);
        assertThatThrownBy(() -> service.create(PROJECT, SOURCE,
                        com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_ONLY, longName, "bob"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("255");
    }

    @Test
    void countReturnsRecordingCountWithoutTouchingScenariosOrRuns() {
        service.create(PROJECT, SOURCE, com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "a");
        service.create(PROJECT, SOURCE, com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "b");

        assertThat(service.count(PROJECT)).isEqualTo(2);
    }

    @Test
    void completeComputesRealSizeBytesFromTimeline() {
        Recording r = service.create(PROJECT, SOURCE,
                com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "a");
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        service.appendValues(PROJECT, r.id(), List.of(
                NeutralValue.good("v1", t, 1.0), NeutralValue.good("v1", t.plusMillis(1), 2.0)));

        Recording completed = service.complete(PROJECT, r.id());
        assertThat(completed.sizeBytes()).isPositive();
    }

    @Test
    void deleteRemovesRecordingAndItsValues() {
        Recording r = service.create(PROJECT, SOURCE,
                com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "a");
        service.appendValues(PROJECT, r.id(), List.of(
                NeutralValue.good("v1", Instant.parse("2026-01-01T00:00:00Z"), 1.0)));

        service.delete(PROJECT, r.id(), "alice");

        assertThatThrownBy(() -> service.get(PROJECT, r.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteLostRaceIsANoOpAndDoesNotEmitActivity() {
        Recording r = service.create(PROJECT, SOURCE,
                com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "a");

        // Simulates two concurrent deletes both passing requireRecording before either
        // commits: the repository row is already gone by the time deleteById runs.
        RecordingRepository staleRow = new RecordingRepository() {
            @Override
            public RecordingRow create(String projectId, String dataSourceId, String protocol,
                    int schemaVersion, String origin, String scanType, String name, String createdBy,
                    String schemaNodesJson) {
                throw new UnsupportedOperationException();
            }

            @Override
            public java.util.Optional<RecordingRow> findById(String id) {
                return java.util.Optional.of(new RecordingRow(id, PROJECT, SOURCE, "OPC_UA", 1, "SCAN_RECORD",
                        "SCHEMA_AND_DATA", null, null, null, 0, 0, OffsetDateTime.now(ZoneOffset.UTC),
                        OffsetDateTime.now(ZoneOffset.UTC), "a", 0, "[]"));
            }

            @Override
            public List<RecordingRow> findByProject(String projectId) {
                throw new UnsupportedOperationException();
            }

            @Override
            public List<RecordingRow> findByProjectPaged(String projectId, OffsetDateTime afterAt,
                    String afterId, int limit) {
                throw new UnsupportedOperationException();
            }

            @Override
            public RecordingRow finalizeStats(String id, OffsetDateTime timeStart, OffsetDateTime timeEnd,
                    long valueCount, long sizeBytes) {
                throw new UnsupportedOperationException();
            }

            @Override
            public boolean deleteById(String id) {
                return false;
            }

            @Override
            public long countByProject(String projectId) {
                throw new UnsupportedOperationException();
            }
        };
        CountingActivityEventRepository activityLog = new CountingActivityEventRepository();
        RecordingService raced = new RecordingService(staleRow, new InMemoryValueTimelineRepository(),
                sources, schemas, new InMemoryCredentialStore(), capturer, fakeProjects(),
                new ActivityEventService(activityLog), scenarios, runs, evidence, new ObjectMapper());

        raced.delete(PROJECT, r.id(), "alice");

        assertThat(activityLog.count).isZero();
    }

    private static final class CountingActivityEventRepository
            implements com.ainclusive.iotsim.persistence.activityevent.ActivityEventRepository {
        int count;

        @Override
        public com.ainclusive.iotsim.persistence.activityevent.ActivityEventRow append(String projectId,
                String actor, String action, String objectType, String objectId, String detailJson) {
            count++;
            return new com.ainclusive.iotsim.persistence.activityevent.ActivityEventRow(0, projectId, actor,
                    action, objectType, objectId, OffsetDateTime.now(ZoneOffset.UTC), "{}");
        }

        @Override
        public List<com.ainclusive.iotsim.persistence.activityevent.ActivityEventRow> query(
                com.ainclusive.iotsim.persistence.activityevent.ActivityEventQuery filter) {
            return List.of();
        }
    }

    @Test
    void deleteMissingRecordingThrowsNotFound() {
        assertThatThrownBy(() -> service.delete(PROJECT, "nope", "alice"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteBlockedByScenarioStepStillTargetingIt() {
        Recording r = service.create(PROJECT, SOURCE,
                com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "a");
        ScenarioStepRow step = new ScenarioStepRow(0, "REPLAY", SOURCE,
                "{\"recordingId\":\"" + r.id() + "\"}");
        scenarios.add(new ScenarioRow("scn-1", PROJECT, "Smoke", "READY", "{}", List.of(step),
                OffsetDateTime.now(ZoneOffset.UTC), OffsetDateTime.now(ZoneOffset.UTC), "local", 0));

        assertThatThrownBy(() -> service.delete(PROJECT, r.id(), "alice"))
                .isInstanceOf(RetentionDependencyException.class)
                .hasMessageContaining(r.id());

        // rejected delete leaves the recording in place
        assertThat(service.get(PROJECT, r.id()).id()).isEqualTo(r.id());
    }

    @Test
    void deleteBlockedByActiveRunReplayingIt() {
        Recording r = service.create(PROJECT, SOURCE,
                com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "a");
        evidence.add(new EvidenceRow("ev-1", PROJECT, "run-1", "CAPTURING",
                "{\"recordingId\":\"" + r.id() + "\"}", null, OffsetDateTime.now(ZoneOffset.UTC), "local"));
        runs.add(new RunRow("run-1", PROJECT, "REPLAY", "MANUAL", "local", "RUNNING", null, "ev-1",
                OffsetDateTime.now(ZoneOffset.UTC), null, OffsetDateTime.now(ZoneOffset.UTC),
                List.of(SOURCE), null));

        assertThatThrownBy(() -> service.delete(PROJECT, r.id(), "alice"))
                .isInstanceOf(RetentionDependencyException.class);
    }

    @Test
    void getReflectsHasDependentsAndLastUsedAtFromRunHistory() {
        Recording r = service.create(PROJECT, SOURCE,
                com.ainclusive.iotsim.protocolmodel.ScanType.SCHEMA_AND_DATA, null, "a");
        assertThat(service.get(PROJECT, r.id()).hasDependents()).isFalse();
        assertThat(service.get(PROJECT, r.id()).lastUsedAt()).isNull();

        OffsetDateTime replayedAt = OffsetDateTime.parse("2026-02-01T00:00:00Z");
        evidence.add(new EvidenceRow("ev-2", PROJECT, "run-2", "READY",
                "{\"recordingId\":\"" + r.id() + "\"}", null, replayedAt, "local"));
        runs.add(new RunRow("run-2", PROJECT, "REPLAY", "MANUAL", "local", "COMPLETED", null, "ev-2",
                replayedAt, replayedAt.plusMinutes(1), replayedAt, List.of(SOURCE), null));

        Recording after = service.get(PROJECT, r.id());
        assertThat(after.lastUsedAt()).isEqualTo(replayedAt.toInstant());
        // a completed (non-active) run does not block deletion
        assertThat(after.hasDependents()).isFalse();
    }

    private static ProjectRepository fakeProjects() {
        return new ProjectRepository() {
            @Override public Optional<ProjectRow> findById(String id) { return Optional.empty(); }
            @Override public ProjectRow insert(String n, String d, String c) { throw new UnsupportedOperationException(); }
            @Override public List<ProjectRow> findAll() { return List.of(); }
            @Override public List<ProjectRow> findAllPaged(String s, java.time.OffsetDateTime a, String i, int l) { return List.of(); }
            @Override public Optional<ProjectRow> update(String id, String n, String d, long v) { throw new UnsupportedOperationException(); }
            @Override public Optional<ProjectRow> archive(String id) { throw new UnsupportedOperationException(); }
            @Override public boolean deleteById(String id) { throw new UnsupportedOperationException(); }
        };
    }

    private static SchemaNode variable(String nodeId, DataType type) {
        return new SchemaNode(nodeId, null, "/" + nodeId, nodeId, NodeKind.VARIABLE,
                type, ValueRank.SCALAR, Access.READ, null, null);
    }

    /** Captures the sink so a test can emit live value batches, and records stop(). */
    private static final class FakeCapturer implements SourceCapturer {
        private CaptureSpec spec;
        private Consumer<List<NeutralValue>> sink;
        private boolean stopped;

        @Override
        public CaptureSession startCapture(CaptureSpec spec, Consumer<List<NeutralValue>> sink) {
            this.spec = spec;
            this.sink = sink;
            return () -> stopped = true;
        }

        void emit(List<NeutralValue> values) {
            sink.accept(values);
        }
    }

    private static final class FakeSchemaRepository implements SchemaRepository {
        private Supplier<Optional<SchemaWithNodes>> current = Optional::empty;
        private final Map<String, SchemaWithNodes> byVersion = new HashMap<>();
        int findByVersionCalls;

        void set(int version, List<SchemaNode> nodes) {
            SchemaWithNodes schema = new SchemaWithNodes(
                    "sch-1", SOURCE, version, OffsetDateTime.now(ZoneOffset.UTC), nodes);
            current = () -> Optional.of(schema);
        }

        void setByVersion(String dataSourceId, int version, List<SchemaNode> nodes) {
            byVersion.put(dataSourceId + "@" + version,
                    new SchemaWithNodes("sch-" + version, dataSourceId, version,
                            OffsetDateTime.now(ZoneOffset.UTC), nodes));
        }

        void clear() {
            current = Optional::empty;
        }

        @Override
        public Optional<SchemaWithNodes> findCurrent(String dataSourceId) {
            return current.get();
        }

        @Override
        public Optional<SchemaWithNodes> findByVersion(String dataSourceId, int version) {
            findByVersionCalls++;
            return Optional.ofNullable(byVersion.get(dataSourceId + "@" + version));
        }

        @Override
        public SchemaWithNodes saveNewVersion(String dataSourceId, List<SchemaNode> nodes) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryRecordingRepository implements RecordingRepository {
        final Map<String, RecordingRow> rows = new HashMap<>();
        private int seq;

        @Override
        public RecordingRow create(String projectId, String dataSourceId, String protocol,
                int schemaVersion, String origin, String scanType, String name, String createdBy,
                String schemaNodesJson) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            RecordingRow row = new RecordingRow("rec-" + (++seq), projectId, dataSourceId, protocol,
                    schemaVersion, origin, scanType, name, null, null, 0, 0, now, now, createdBy, 0,
                    schemaNodesJson != null && !schemaNodesJson.isBlank() ? schemaNodesJson : "[]");
            rows.put(row.id(), row);
            return row;
        }

        @Override
        public Optional<RecordingRow> findById(String id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<RecordingRow> findByProject(String projectId) {
            return rows.values().stream().filter(r -> r.projectId().equals(projectId)).toList();
        }

        @Override
        public List<RecordingRow> findByProjectPaged(String projectId,
                OffsetDateTime afterAt, String afterId, int limit) {
            return rows.values().stream()
                    .filter(r -> r.projectId().equals(projectId))
                    .filter(r -> afterAt == null || r.createdAt().isBefore(afterAt)
                            || (r.createdAt().isEqual(afterAt) && r.id().compareTo(afterId) < 0))
                    .sorted(java.util.Comparator.comparing(RecordingRow::createdAt).reversed()
                            .thenComparing(java.util.Comparator.comparing(RecordingRow::id).reversed()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public RecordingRow finalizeStats(String id, OffsetDateTime timeStart, OffsetDateTime timeEnd,
                long valueCount, long sizeBytes) {
            RecordingRow r = rows.get(id);
            RecordingRow updated = new RecordingRow(r.id(), r.projectId(), r.dataSourceId(), r.protocol(),
                    r.schemaVersion(), r.origin(), r.scanType(), r.name(), timeStart, timeEnd, valueCount,
                    sizeBytes, r.createdAt(), OffsetDateTime.now(ZoneOffset.UTC), r.createdBy(),
                    r.version() + 1, r.schemaNodesJson());
            rows.put(id, updated);
            return updated;
        }

        @Override
        public boolean deleteById(String id) {
            return rows.remove(id) != null;
        }

        @Override
        public long countByProject(String projectId) {
            return rows.values().stream().filter(r -> r.projectId().equals(projectId)).count();
        }
    }

    private static final class InMemoryValueTimelineRepository implements ValueTimelineRepository {
        private final Map<String, List<NeutralValue>> byRecording = new HashMap<>();

        @Override
        public long append(String recordingId, List<NeutralValue> values) {
            byRecording.computeIfAbsent(recordingId, k -> new ArrayList<>()).addAll(values);
            return values.size();
        }

        @Override
        public long sumBytes(String recordingId) {
            return byRecording.getOrDefault(recordingId, List.of()).size() * 8L;
        }

        @Override
        public void deleteByRecording(String recordingId) {
            byRecording.remove(recordingId);
        }

        @Override
        public List<NeutralValue> readRange(String recordingId, Instant from, Instant to) {
            return byRecording.getOrDefault(recordingId, List.of()).stream()
                    .filter(v -> !v.sourceTime().isBefore(from) && !v.sourceTime().isAfter(to))
                    .toList();
        }

        @Override
        public List<NeutralValue> readAll(String recordingId) {
            return List.copyOf(byRecording.getOrDefault(recordingId, List.of()));
        }

        @Override
        public long count(String recordingId) {
            return byRecording.getOrDefault(recordingId, List.of()).size();
        }

        @Override
        public List<ValueTimelineEntry> readPage(String recordingId, long afterSeq, int limit,
                com.ainclusive.iotsim.protocolmodel.ValueFilter filter) {
            List<NeutralValue> all = byRecording.getOrDefault(recordingId, List.of());
            List<ValueTimelineEntry> result = new ArrayList<>();
            for (int i = 0; i < all.size(); i++) {
                if (i > afterSeq) {
                    result.add(new ValueTimelineEntry(i, all.get(i).nodeId(), all.get(i)));
                    if (result.size() >= limit) {
                        break;
                    }
                }
            }
            return result;
        }
    }

    private static final class FakeDataSourceRepository implements DataSourceRepository {
        private final String id;
        private final String projectId;
        private String realDeviceEndpoint;

        FakeDataSourceRepository(String id, String projectId, String realDeviceEndpoint) {
            this.id = id;
            this.projectId = projectId;
            this.realDeviceEndpoint = realDeviceEndpoint;
        }

        @Override
        public Optional<DataSourceRow> findById(String id) {
            if (!this.id.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new DataSourceRow(id, projectId, "src", "OPC_UA", "SCAN",
                    "sch-1", 2, 0, realDeviceEndpoint, "{}", null, false, now, now, "local", 0));
        }

        @Override
        public DataSourceRow insert(String projectId, String name, String protocol, String basis,
                int simulatorPort, String realDeviceEndpoint, String runtimeConfigJson,
                String securityConfigJson, String createdBy) {
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
        public Optional<DataSourceRow> update(String id, String name, int simulatorPort,
                String realDeviceEndpoint, String runtimeConfigJson, String securityConfigJson,
                boolean enabled, long expectedVersion) {
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

    /** Minimal scenario store: only what {@code RecordingService}'s dependency check reads. */
    private static final class InMemoryScenarioRepository implements ScenarioRepository {
        private final List<ScenarioRow> rows = new ArrayList<>();

        void add(ScenarioRow row) {
            rows.add(row);
        }

        @Override
        public ScenarioRow create(String projectId, String name, String deterministicSettings,
                List<ScenarioStepInput> steps, String createdBy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ScenarioRow> findById(String id) {
            return rows.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public List<ScenarioRow> findByProject(String projectId) {
            return rows.stream().filter(r -> r.projectId().equals(projectId)).toList();
        }

        @Override
        public List<ScenarioRow> findByProjectPaged(String projectId, OffsetDateTime afterAt,
                String afterId, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ScenarioRow> update(String id, String name, String deterministicSettings,
                List<ScenarioStepInput> steps, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ScenarioRow> updateStatus(String id, String status) {
            throw new UnsupportedOperationException();
        }
    }

    /** Minimal run store: only what {@code RecordingService}'s dependency/last-used lookups read. */
    private static final class InMemoryRunRepository implements RunRepository {
        private final List<RunRow> rows = new ArrayList<>();

        void add(RunRow row) {
            rows.add(row);
        }

        @Override
        public RunRow create(String projectId, String kind, String trigger, String initiator,
                List<String> sourceIds, String scenarioId, String parentRunId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<RunRow> findById(String id) {
            return rows.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public List<RunRow> findByProject(String projectId) {
            return rows.stream().filter(r -> r.projectId().equals(projectId)).toList();
        }

        @Override
        public List<RunRow> findActiveByProject(String projectId) {
            return rows.stream()
                    .filter(r -> r.projectId().equals(projectId))
                    .filter(r -> "RUNNING".equals(r.state()) || "QUEUED".equals(r.state()))
                    .toList();
        }

        @Override
        public RunRow start(String id, OffsetDateTime startedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RunRow end(String id, String terminalState, OffsetDateTime endedAt) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RunRow linkEvidence(String runId, String evidenceId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RunRow> findByProjectPaged(String projectId, OffsetDateTime afterAt,
                String afterId, int limit) {
            throw new UnsupportedOperationException();
        }
    }

    /** Minimal evidence store: only what {@code RecordingService}'s manifest lookups read. */
    private static final class InMemoryEvidenceRepository implements EvidenceRepository {
        private final Map<String, EvidenceRow> rows = new HashMap<>();

        void add(EvidenceRow row) {
            rows.put(row.id(), row);
        }

        @Override
        public EvidenceRow create(String projectId, String runId, String createdBy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<EvidenceRow> findById(String id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<EvidenceRow> findByRun(String runId) {
            return rows.values().stream().filter(e -> runId.equals(e.runId())).findFirst();
        }

        @Override
        public List<EvidenceRow> findByProject(String projectId) {
            return rows.values().stream().filter(e -> e.projectId().equals(projectId)).toList();
        }

        @Override
        public List<EvidenceRow> findByProjectPaged(String projectId, OffsetDateTime afterAt,
                String afterId, int limit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EvidenceRow updateManifest(String id, String manifestJson) {
            throw new UnsupportedOperationException();
        }

        @Override
        public EvidenceRow updateStatus(String id, String status, String objectRef) {
            throw new UnsupportedOperationException();
        }
    }
}
