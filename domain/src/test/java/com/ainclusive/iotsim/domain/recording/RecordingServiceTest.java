package com.ainclusive.iotsim.domain.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.project.ProjectRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
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

class RecordingServiceTest {

    private static final String PROJECT = "proj-1";
    private static final String SOURCE = "ds-1";
    private static final String ENDPOINT = "opc.tcp://real:4840/iotsim";

    private RecordingService service;
    private FakeDataSourceRepository sources;
    private FakeSchemaRepository schemas;
    private FakeCapturer capturer;

    @BeforeEach
    void setUp() {
        sources = new FakeDataSourceRepository(SOURCE, PROJECT, ENDPOINT);
        schemas = new FakeSchemaRepository();
        capturer = new FakeCapturer();
        service = new RecordingService(
                new InMemoryRecordingRepository(),
                new InMemoryValueTimelineRepository(),
                sources,
                schemas,
                new InMemoryCredentialStore(),
                capturer,
                fakeProjects());
    }

    @Test
    void createUnderExistingSource() {
        Recording r = service.create(PROJECT, SOURCE, "alice");
        assertThat(r.origin()).isEqualTo("SCAN_RECORD");
        assertThat(r.valueCount()).isZero();
        assertThat(r.dataSourceId()).isEqualTo(SOURCE);
    }

    @Test
    void createUnderMissingSourceThrowsNotFound() {
        assertThatThrownBy(() -> service.create(PROJECT, "nope", "a"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void appendThenCompleteUpdatesValueCount() {
        Recording r = service.create(PROJECT, SOURCE, "a");
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
        service.create(PROJECT, SOURCE, "alice");
        service.create(PROJECT, SOURCE, "bob");
        service.create(PROJECT, SOURCE, "carol");

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
                capturer, existingProject);
        svc.create(PROJECT, SOURCE, "alice");
        svc.create(PROJECT, SOURCE, "bob");
        svc.create(PROJECT, SOURCE, "carol");

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
        String rid = svc.create(PROJECT, SOURCE, "test").id();
        Instant t = Instant.parse("2024-01-01T00:00:00Z");
        List<NeutralValue> vals = List.of(
                NeutralValue.good("n1", t, 1.0),
                NeutralValue.good("n2", t.plusSeconds(1), 2.0),
                NeutralValue.good("n3", t.plusSeconds(2), 3.0));
        svc.appendValues(PROJECT, rid, vals);

        var page = svc.listValues(PROJECT, rid, null, 2);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).parameterId()).isEqualTo("n1");
        assertThat(page.nextCursor()).isNotNull();

        var page2 = svc.listValues(PROJECT, rid, page.nextCursor(), 2);
        assertThat(page2.items()).hasSize(1);
        assertThat(page2.items().get(0).parameterId()).isEqualTo("n3");
        assertThat(page2.nextCursor()).isNull();
    }

    @Test
    void listValuesThrowsForMissingRecording() {
        assertThatThrownBy(() -> service.listValues(PROJECT, "no-such-id", null, null))
                .isInstanceOf(com.ainclusive.iotsim.domain.common.ResourceNotFoundException.class);
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

        void set(int version, List<SchemaNode> nodes) {
            SchemaWithNodes schema = new SchemaWithNodes(
                    "sch-1", SOURCE, version, OffsetDateTime.now(ZoneOffset.UTC), nodes);
            current = () -> Optional.of(schema);
        }

        void clear() {
            current = Optional::empty;
        }

        @Override
        public Optional<SchemaWithNodes> findCurrent(String dataSourceId) {
            return current.get();
        }

        @Override
        public SchemaWithNodes saveNewVersion(String dataSourceId, List<SchemaNode> nodes) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class InMemoryRecordingRepository implements RecordingRepository {
        private final Map<String, RecordingRow> rows = new HashMap<>();
        private int seq;

        @Override
        public RecordingRow create(String projectId, String dataSourceId, int schemaVersion,
                String origin, String createdBy) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            RecordingRow row = new RecordingRow("rec-" + (++seq), projectId, dataSourceId,
                    schemaVersion, origin, null, null, 0, 0, now, now, createdBy, 0);
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
            RecordingRow updated = new RecordingRow(r.id(), r.projectId(), r.dataSourceId(),
                    r.schemaVersion(), r.origin(), timeStart, timeEnd, valueCount, sizeBytes,
                    r.createdAt(), OffsetDateTime.now(ZoneOffset.UTC), r.createdBy(), r.version() + 1);
            rows.put(id, updated);
            return updated;
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
        public List<ValueTimelineEntry> readPage(String recordingId, long afterSeq, int limit) {
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
}
