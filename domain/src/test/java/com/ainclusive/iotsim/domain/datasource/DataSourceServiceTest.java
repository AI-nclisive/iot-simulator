package com.ainclusive.iotsim.domain.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.project.ProjectRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.platform.runtime.InMemoryRuntimeController;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DataSourceServiceTest {

    private static final String PROJECT = "proj-1";

    private DataSourceService service;

    @BeforeEach
    void setUp() {
        service = new DataSourceService(
                new InMemoryDataSourceRepository(),
                new FakeProjectRepository(Set.of(PROJECT)),
                new EmptySchemaRepository(),
                new InMemoryRuntimeController());
    }

    @Test
    void createUnderExistingProjectStartsStoppedAtVersionZero() {
        DataSource ds = service.create(PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, "alice");
        assertThat(ds.id()).isNotBlank();
        assertThat(ds.protocol()).isEqualTo(Protocol.OPC_UA);
        assertThat(ds.basis()).isEqualTo(SourceBasis.MANUAL);
        assertThat(ds.runtimeState()).isEqualTo(RuntimeState.STOPPED);
        assertThat(ds.enabled()).isFalse();
        assertThat(ds.version()).isZero();
    }

    @Test
    void createUnderMissingProjectThrowsNotFound() {
        assertThatThrownBy(() -> service.create("nope", "Pump", "OPC_UA", "MANUAL", null, null, "a"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createWithInvalidProtocolThrowsBadInput() {
        assertThatThrownBy(() -> service.create(PROJECT, "Pump", "NOPE", "MANUAL", null, null, "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getFromWrongProjectThrowsNotFound() {
        DataSource ds = service.create(PROJECT, "Pump", "MODBUS_TCP", "SCAN", null, null, "a");
        assertThatThrownBy(() -> service.get("other-project", ds.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void startThenStopTogglesRuntimeState() {
        DataSource ds = service.create(PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, "a");
        assertThat(service.start(PROJECT, ds.id()).runtimeState()).isEqualTo(RuntimeState.RUNNING);
        assertThat(service.stop(PROJECT, ds.id()).runtimeState()).isEqualTo(RuntimeState.STOPPED);
    }

    @Test
    void updateWithStaleVersionThrowsConflict() {
        DataSource ds = service.create(PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, "a");
        assertThatThrownBy(() -> service.update(PROJECT, ds.id(), "x", null, null, true, ds.version() + 5))
                .isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void deleteRemovesSource() {
        DataSource ds = service.create(PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, "a");
        service.delete(PROJECT, ds.id());
        assertThatThrownBy(() -> service.get(PROJECT, ds.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static final class InMemoryDataSourceRepository implements DataSourceRepository {
        private final List<DataSourceRow> rows = new ArrayList<>();
        private int seq;

        @Override
        public DataSourceRow insert(String projectId, String name, String protocol, String basis,
                String endpointJson, String runtimeConfigJson, String createdBy) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            DataSourceRow row = new DataSourceRow(
                    "ds-" + (++seq), projectId, name, protocol, basis, null, null,
                    endpointJson != null ? endpointJson : "{}",
                    runtimeConfigJson != null ? runtimeConfigJson : "{}",
                    false, now, now, createdBy, 0);
            rows.add(row);
            return row;
        }

        @Override
        public List<DataSourceRow> findByProject(String projectId) {
            return rows.stream().filter(r -> r.projectId().equals(projectId)).toList();
        }

        @Override
        public Optional<DataSourceRow> findById(String id) {
            return rows.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public Optional<DataSourceRow> update(String id, String name, String endpointJson,
                String runtimeConfigJson, boolean enabled, long expectedVersion) {
            for (int i = 0; i < rows.size(); i++) {
                DataSourceRow r = rows.get(i);
                if (r.id().equals(id) && r.version() == expectedVersion) {
                    DataSourceRow updated = new DataSourceRow(
                            r.id(), r.projectId(), name, r.protocol(), r.basis(), r.schemaId(),
                            r.schemaVersion(), endpointJson, runtimeConfigJson, enabled,
                            r.createdAt(), OffsetDateTime.now(ZoneOffset.UTC), r.createdBy(), r.version() + 1);
                    rows.set(i, updated);
                    return Optional.of(updated);
                }
            }
            return Optional.empty();
        }

        @Override
        public boolean deleteById(String id) {
            return rows.removeIf(r -> r.id().equals(id));
        }
    }

    private static final class FakeProjectRepository implements ProjectRepository {
        private final Set<String> existing;

        FakeProjectRepository(Set<String> existing) {
            this.existing = existing;
        }

        @Override
        public Optional<ProjectRow> findById(String id) {
            if (!existing.contains(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new ProjectRow(id, "p", null, "ACTIVE", now, now, "local", 0));
        }

        @Override
        public ProjectRow insert(String name, String description, String createdBy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ProjectRow> findAll() {
            return List.of();
        }

        @Override
        public Optional<ProjectRow> update(String id, String name, String description, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class EmptySchemaRepository implements SchemaRepository {
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
