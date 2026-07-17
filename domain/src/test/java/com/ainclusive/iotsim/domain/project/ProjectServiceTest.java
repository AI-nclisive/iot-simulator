package com.ainclusive.iotsim.domain.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.project.ProjectRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ProjectServiceTest {

    private ProjectService service;
    private InMemoryProjectRepository projectRepo;
    private InMemoryDataSourceRepository dsRepo;
    private InMemorySchemaRepository schemaRepo;
    private InMemoryRecordingRepository recordingRepo;

    @BeforeEach
    void setUp() {
        projectRepo = new InMemoryProjectRepository();
        dsRepo = new InMemoryDataSourceRepository();
        schemaRepo = new InMemorySchemaRepository();
        recordingRepo = new InMemoryRecordingRepository();
        service = new ProjectService(projectRepo, dsRepo, schemaRepo, recordingRepo);
    }

    @Test
    void createReturnsActiveProjectAtVersionZero() {
        Project p = service.create("Line 1", "desc", "alice");
        assertThat(p.id()).isNotBlank();
        assertThat(p.name()).isEqualTo("Line 1");
        assertThat(p.status()).isEqualTo(Project.ProjectStatus.ACTIVE);
        assertThat(p.version()).isZero();
        assertThat(p.createdBy()).isEqualTo("alice");
    }

    @Test
    void getMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.get("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateWithCurrentVersionIncrementsVersion() {
        Project p = service.create("a", null, "local");
        Project updated = service.update(p.id(), "b", "changed", p.version());
        assertThat(updated.name()).isEqualTo("b");
        assertThat(updated.version()).isEqualTo(1L);
    }

    @Test
    void updateWithStaleVersionThrowsConflict() {
        Project p = service.create("a", null, "local");
        assertThatThrownBy(() -> service.update(p.id(), "b", null, p.version() + 99))
                .isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void updateMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.update("nope", "b", null, 0))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.delete("nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // duplicate() — happy path
    // -------------------------------------------------------------------------

    @Test
    void duplicateCreatesCopyWithSuffix() {
        Project src = service.create("Factory", "desc", "alice");
        Project copy = service.duplicate(src.id());

        assertThat(copy.id()).isNotEqualTo(src.id());
        assertThat(copy.name()).isEqualTo("Factory (copy)");
        assertThat(copy.description()).isEqualTo("desc");
        assertThat(copy.createdBy()).isEqualTo("alice");
        assertThat(copy.version()).isZero();
    }

    @Test
    void duplicateCopiesDataSources() {
        Project src = service.create("Factory", null, "alice");
        dsRepo.insert(src.id(), "Sensor A", "OPC_UA", "SCAN", 0, null, null, null, "alice");
        dsRepo.insert(src.id(), "Sensor B", "MODBUS_TCP", "MANUAL", 0, null, null, null, "alice");

        Project copy = service.duplicate(src.id());

        List<DataSourceRow> copiedDs = dsRepo.findByProject(copy.id());
        assertThat(copiedDs).hasSize(2);
        assertThat(copiedDs.stream().map(DataSourceRow::name))
                .containsExactlyInAnyOrder("Sensor A", "Sensor B");
        // Copies must have new IDs distinct from the originals.
        List<DataSourceRow> originalDs = dsRepo.findByProject(src.id());
        assertThat(copiedDs.stream().map(DataSourceRow::id))
                .doesNotContainAnyElementsOf(originalDs.stream().map(DataSourceRow::id).toList());
    }

    @Test
    void duplicateCopiesRecordingsWithUpdatedDataSourceIds() {
        Project src = service.create("Factory", null, "alice");
        DataSourceRow ds = dsRepo.insert(src.id(), "Sensor A", "OPC_UA", "SCAN", 0, null, null, null, "alice");
        recordingRepo.create(src.id(), ds.id(), "OPC_UA", 1, "SCAN_RECORD", "SCHEMA_AND_DATA", null, "alice", "[]");

        Project copy = service.duplicate(src.id());

        List<RecordingRow> copiedRecs = recordingRepo.findByProject(copy.id());
        assertThat(copiedRecs).hasSize(1);
        // The copied recording must point to the new data source, not the original.
        List<DataSourceRow> copiedDs = dsRepo.findByProject(copy.id());
        assertThat(copiedRecs.get(0).dataSourceId()).isEqualTo(copiedDs.get(0).id());
        assertThat(copiedRecs.get(0).dataSourceId()).isNotEqualTo(ds.id());
    }

    @Test
    void duplicateCopiesSchemaToNewDataSource() {
        Project src = service.create("Factory", null, "alice");
        DataSourceRow ds = dsRepo.insert(src.id(), "Sensor A", "OPC_UA", "SCAN", 0, null, null, null, "alice");
        SchemaNode node = new SchemaNode("n1", null, "/root", "root", NodeKind.FOLDER,
                null, null, null, null, null);
        schemaRepo.saveNewVersion(ds.id(), List.of(node));

        Project copy = service.duplicate(src.id());

        DataSourceRow copiedDs = dsRepo.findByProject(copy.id()).get(0);
        assertThat(schemaRepo.findCurrent(copiedDs.id())).isPresent();
        assertThat(schemaRepo.findCurrent(ds.id())).isPresent(); // original untouched
    }

    @Test
    void duplicateMissingSourceThrowsNotFound() {
        assertThatThrownBy(() -> service.duplicate("no-such-project"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // -------------------------------------------------------------------------
    // archive()
    // -------------------------------------------------------------------------

    @Test
    void archiveSetsStatusToArchived() {
        Project p = service.create("Factory", null, "alice");
        Project archived = service.archive(p.id());
        assertThat(archived.status()).isEqualTo(Project.ProjectStatus.ARCHIVED);
    }

    @Test
    void archiveMissingProjectThrowsNotFound() {
        assertThatThrownBy(() -> service.archive("no-such-project"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listPagedEmitsCursorWhenResultsExceedLimit() {
        service.create("A", null, "it");
        service.create("B", null, "it");
        service.create("C", null, "it");

        Page<Project> page = service.listPaged(null, null, 2);
        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNotNull();
        assertThat(page.limit()).isEqualTo(2);

        Page<Project> page2 = service.listPaged(null, page.nextCursor(), 2);
        assertThat(page2.items()).hasSize(1);
        assertThat(page2.nextCursor()).isNull();
    }

    // =========================================================================
    // In-memory fakes
    // =========================================================================

    /** Minimal in-memory repository — keeps the service test free of a database. */
    private static final class InMemoryProjectRepository implements ProjectRepository {
        private final List<ProjectRow> rows = new ArrayList<>();
        private int seq;

        @Override
        public ProjectRow insert(String name, String description, String createdBy) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            ProjectRow row = new ProjectRow(
                    "id-" + (++seq), name, description, "ACTIVE", now, now, createdBy, 0);
            rows.add(row);
            return row;
        }

        @Override
        public Optional<ProjectRow> findById(String id) {
            return rows.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public List<ProjectRow> findAll() {
            return List.copyOf(rows);
        }

        @Override
        public List<ProjectRow> findAllPaged(String status, java.time.OffsetDateTime afterAt,
                String afterId, int limit) {
            return rows.stream()
                    .filter(r -> status == null || r.status().equals(status))
                    .filter(r -> afterAt == null || r.createdAt().isBefore(afterAt)
                            || (r.createdAt().isEqual(afterAt) && r.id().compareTo(afterId) < 0))
                    .sorted(java.util.Comparator.comparing(ProjectRow::createdAt).reversed()
                            .thenComparing(java.util.Comparator.comparing(ProjectRow::id).reversed()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<ProjectRow> update(String id, String name, String description, long expectedVersion) {
            for (int i = 0; i < rows.size(); i++) {
                ProjectRow r = rows.get(i);
                if (r.id().equals(id) && r.version() == expectedVersion) {
                    ProjectRow updated = new ProjectRow(
                            id, name, description, r.status(), r.createdAt(),
                            OffsetDateTime.now(ZoneOffset.UTC), r.createdBy(), r.version() + 1);
                    rows.set(i, updated);
                    return Optional.of(updated);
                }
            }
            return Optional.empty();
        }

        @Override
        public Optional<ProjectRow> archive(String id) {
            for (int i = 0; i < rows.size(); i++) {
                ProjectRow r = rows.get(i);
                if (r.id().equals(id)) {
                    ProjectRow archived = new ProjectRow(
                            id, r.name(), r.description(), "ARCHIVED", r.createdAt(),
                            OffsetDateTime.now(ZoneOffset.UTC), r.createdBy(), r.version() + 1);
                    rows.set(i, archived);
                    return Optional.of(archived);
                }
            }
            return Optional.empty();
        }

        @Override
        public boolean deleteById(String id) {
            return rows.removeIf(r -> r.id().equals(id));
        }
    }

    private static final class InMemoryDataSourceRepository implements DataSourceRepository {
        private final List<DataSourceRow> rows = new ArrayList<>();
        private int seq;

        @Override
        public DataSourceRow insert(String projectId, String name, String protocol, String basis,
                int simulatorPort, String realDeviceEndpoint, String runtimeConfigJson,
                String securityConfigJson, String createdBy) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            DataSourceRow row = new DataSourceRow(
                    "ds-" + (++seq), projectId, name, protocol, basis,
                    null, null, simulatorPort, realDeviceEndpoint, runtimeConfigJson, securityConfigJson,
                    false, now, now, createdBy, 0);
            rows.add(row);
            return row;
        }

        @Override
        public List<DataSourceRow> findByProject(String projectId) {
            return rows.stream().filter(r -> r.projectId().equals(projectId)).toList();
        }

        @Override
        public List<DataSourceRow> findByProjectPaged(String projectId, String protocol,
                java.time.OffsetDateTime afterAt, String afterId, int limit) {
            return List.of();
        }

        @Override
        public Optional<DataSourceRow> findById(String id) {
            return rows.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public Optional<DataSourceRow> duplicate(String sourceId, String newName, String createdBy) {
            return rows.stream()
                    .filter(r -> r.id().equals(sourceId))
                    .findFirst()
                    .map(source -> {
                        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                        DataSourceRow copy = new DataSourceRow(
                                "ds-" + (++seq), source.projectId(), newName,
                                source.protocol(), source.basis(), null, null,
                                source.simulatorPort(), source.realDeviceEndpoint(), source.runtimeConfig(),
                                source.securityConfig(), false, now, now, createdBy, 0);
                        rows.add(copy);
                        return copy;
                    });
        }

        @Override
        public Optional<DataSourceRow> update(String id, String name, int simulatorPort,
                String realDeviceEndpoint, String runtimeConfigJson, String securityConfigJson,
                boolean enabled, long expectedVersion) {
            return Optional.empty();
        }

        @Override
        public boolean deleteById(String id) {
            return rows.removeIf(r -> r.id().equals(id));
        }
    }

    private static final class InMemorySchemaRepository implements SchemaRepository {
        private final Map<String, SchemaWithNodes> store = new HashMap<>();
        private int seq;

        @Override
        public Optional<SchemaWithNodes> findCurrent(String dataSourceId) {
            return Optional.ofNullable(store.get(dataSourceId));
        }

        @Override
        public SchemaWithNodes saveNewVersion(String dataSourceId, List<SchemaNode> nodes) {
            SchemaWithNodes sw = new SchemaWithNodes(
                    "schema-" + (++seq), dataSourceId, 1, OffsetDateTime.now(ZoneOffset.UTC), nodes);
            store.put(dataSourceId, sw);
            return sw;
        }
    }

    private static final class InMemoryRecordingRepository implements RecordingRepository {
        private final List<RecordingRow> rows = new ArrayList<>();
        private int seq;

        @Override
        public RecordingRow create(String projectId, String dataSourceId, String protocol,
                int schemaVersion, String origin, String scanType, String name, String createdBy,
                String schemaNodesJson) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            RecordingRow row = new RecordingRow(
                    "rec-" + (++seq), projectId, dataSourceId, protocol, schemaVersion, origin, scanType,
                    name, null, null, 0L, 0L, now, now, createdBy, 0,
                    schemaNodesJson != null ? schemaNodesJson : "[]");
            rows.add(row);
            return row;
        }

        @Override
        public Optional<RecordingRow> findById(String id) {
            return rows.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public List<RecordingRow> findByProject(String projectId) {
            return rows.stream().filter(r -> r.projectId().equals(projectId)).toList();
        }

        @Override
        public List<RecordingRow> findByProjectPaged(String projectId,
                java.time.OffsetDateTime afterAt, String afterId, int limit) {
            return List.of();
        }

        @Override
        public RecordingRow finalizeStats(String id, java.time.OffsetDateTime timeStart,
                java.time.OffsetDateTime timeEnd, long valueCount, long sizeBytes) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public long countByProject(String projectId) {
            return rows.stream().filter(r -> r.projectId().equals(projectId)).count();
        }
    }
}
