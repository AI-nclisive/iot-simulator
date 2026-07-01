package com.ainclusive.iotsim.domain.datasource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.PortInUseException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.project.ProjectRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.platform.runtime.InMemoryRuntimeController;
import com.ainclusive.iotsim.platform.secret.ConnectionCredentials;
import com.ainclusive.iotsim.platform.secret.InMemoryCredentialStore;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class DataSourceServiceTest {

    private static final String PROJECT = "proj-1";

    private DataSourceService service;
    private InMemoryCredentialStore credentials;
    private InMemoryDataSourceRepository repository;

    @BeforeEach
    void setUp() {
        credentials = new InMemoryCredentialStore();
        repository = new InMemoryDataSourceRepository();
        service = new DataSourceService(
                repository,
                new FakeProjectRepository(Set.of(PROJECT)),
                new EmptySchemaRepository(),
                new InMemoryRuntimeController(),
                credentials,
                new ObjectMapper());
    }

    @Test
    void createUnderExistingProjectStartsStoppedAtVersionZero() {
        DataSource ds = service.create(PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, null, null, "alice");
        assertThat(ds.id()).isNotBlank();
        assertThat(ds.protocol()).isEqualTo(Protocol.OPC_UA);
        assertThat(ds.basis()).isEqualTo(SourceBasis.MANUAL);
        assertThat(ds.runtimeState()).isEqualTo(RuntimeState.STOPPED);
        assertThat(ds.enabled()).isFalse();
        assertThat(ds.version()).isZero();
    }

    @Test
    void createUnderMissingProjectThrowsNotFound() {
        assertThatThrownBy(() -> service.create("nope", "Pump", "OPC_UA", "MANUAL", null, null, null, null, "a"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createWithInvalidProtocolThrowsBadInput() {
        assertThatThrownBy(() -> service.create(PROJECT, "Pump", "NOPE", "MANUAL", null, null, null, null, "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithMalformedRuntimeConfigThrowsBadInput() {
        // Malformed JSON in a jsonb field must surface as 400, not a 500 from the DB driver.
        assertThatThrownBy(() -> service.create(
                        PROJECT, "Pump", "OPC_UA", "MANUAL", "opc.tcp://plc:4840", "{not json", null, null, "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void updateWithMalformedRuntimeConfigThrowsBadInput() {
        DataSource ds = service.create(PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, null, null, "a");
        assertThatThrownBy(() -> service.update(
                        PROJECT, ds.id(), null, null, "{not json", null, null, ds.version()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getFromWrongProjectThrowsNotFound() {
        DataSource ds = service.create(PROJECT, "Pump", "MODBUS_TCP", "SCAN", null, null, null, null, "a");
        assertThatThrownBy(() -> service.get("other-project", ds.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void startThenStopTogglesRuntimeState() {
        DataSource ds = service.create(PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, null, null, "a");
        assertThat(service.start(PROJECT, ds.id()).runtimeState()).isEqualTo(RuntimeState.RUNNING);
        assertThat(service.stop(PROJECT, ds.id()).runtimeState()).isEqualTo(RuntimeState.STOPPED);
    }

    @Test
    void updateWithStaleVersionThrowsConflict() {
        DataSource ds = service.create(PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, null, null, "a");
        assertThatThrownBy(() -> service.update(PROJECT, ds.id(), "x", null, null, true, null, ds.version() + 5))
                .isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void deleteRemovesSource() {
        DataSource ds = service.create(PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, null, null, "a");
        service.delete(PROJECT, ds.id());
        assertThatThrownBy(() -> service.get(PROJECT, ds.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createWithoutCredentialsIsMissing() {
        DataSource ds = service.create(PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, null, null, "a");
        assertThat(ds.credentialState()).isEqualTo(CredentialState.MISSING);
        assertThat(credentials.has(ds.id())).isFalse();
    }

    @Test
    void createWithPasswordCredentialsIsSessionOnlyAndNeverWrittenToTheRow() {
        ConnectionCredentials creds = ConnectionCredentials.password("operator", "s3cr3t");
        DataSource ds = service.create(PROJECT, "Pump", "OPC_UA", "MANUAL", "{}", "{}", creds, null, "a");

        assertThat(ds.credentialState()).isEqualTo(CredentialState.SESSION_ONLY);
        assertThat(credentials.find(ds.id())).contains(creds);
        // The secret is held only in the credential store, never in the persisted row.
        DataSourceRow row = repository.findById(ds.id()).orElseThrow();
        assertThat(row.endpoint()).doesNotContain("s3cr3t");
        assertThat(row.runtimeConfig()).doesNotContain("s3cr3t");
    }

    @Test
    void createWithAnonymousCredentialsStaysMissing() {
        DataSource ds = service.create(
                PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, ConnectionCredentials.anonymous(), null, "a");
        assertThat(ds.credentialState()).isEqualTo(CredentialState.MISSING);
        assertThat(credentials.has(ds.id())).isFalse();
    }

    @Test
    void updateStoresCredentialsWithoutChangingThemWhenAbsent() {
        DataSource ds = service.create(PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, null, null, "a");

        // Absent credentials leave state unchanged.
        DataSource unchanged = service.update(PROJECT, ds.id(), "Renamed", null, null, true, null, ds.version());
        assertThat(unchanged.credentialState()).isEqualTo(CredentialState.MISSING);

        // Providing credentials stores them session-only and bumps no secret into the row.
        DataSource withCreds = service.update(PROJECT, ds.id(), null, null, null, null,
                ConnectionCredentials.password("u", "pw"), unchanged.version());
        assertThat(withCreds.credentialState()).isEqualTo(CredentialState.SESSION_ONLY);
        assertThat(credentials.has(ds.id())).isTrue();
    }

    @Test
    void clearCredentialsRemovesThem() {
        DataSource ds = service.create(
                PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, ConnectionCredentials.password("u", "pw"), null, "a");
        assertThat(ds.credentialState()).isEqualTo(CredentialState.SESSION_ONLY);

        DataSource cleared = service.clearCredentials(PROJECT, ds.id());
        assertThat(cleared.credentialState()).isEqualTo(CredentialState.MISSING);
        assertThat(credentials.has(ds.id())).isFalse();
    }

    @Test
    void deleteClearsHeldCredentials() {
        DataSource ds = service.create(
                PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, ConnectionCredentials.password("u", "pw"), null, "a");
        service.delete(PROJECT, ds.id());
        assertThat(credentials.has(ds.id())).isFalse();
    }

    @Test
    void staleUpdateNeverStoresTheSecret() {
        DataSource ds = service.create(PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, null, null, "a");
        // A conflicting (stale) update carrying a password must fail the version check
        // AND leave the store untouched: credentials are applied only after the check passes.
        assertThatThrownBy(() -> service.update(PROJECT, ds.id(), "x", null, null, true,
                ConnectionCredentials.password("operator", "s3cr3t"), ds.version() + 5))
                .isInstanceOf(ConcurrencyConflictException.class);
        assertThat(credentials.has(ds.id())).isFalse();
    }

    @Test
    void createWithExternalRefIsSessionOnly() {
        DataSource ds = service.create(PROJECT, "Pump", "OPC_UA", "MANUAL", null, null,
                ConnectionCredentials.externalRef("vault://pump"), null, "a");
        assertThat(ds.credentialState()).isEqualTo(CredentialState.SESSION_ONLY);
        assertThat(credentials.find(ds.id())).map(ConnectionCredentials::mode)
                .contains(ConnectionCredentials.Mode.EXTERNAL_REF);
    }

    @Test
    void updateWithAnonymousClearsExistingCredentials() {
        DataSource ds = service.create(
                PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, ConnectionCredentials.password("u", "pw"), null, "a");
        assertThat(credentials.has(ds.id())).isTrue();

        DataSource cleared = service.update(PROJECT, ds.id(), null, null, null, null,
                ConnectionCredentials.anonymous(), ds.version());
        assertThat(cleared.credentialState()).isEqualTo(CredentialState.MISSING);
        assertThat(credentials.has(ds.id())).isFalse();
    }

    @Test
    void clearCredentialsFromWrongProjectThrowsNotFoundAndKeepsTheSecret() {
        DataSource ds = service.create(
                PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, ConnectionCredentials.password("u", "pw"), null, "a");
        assertThatThrownBy(() -> service.clearCredentials("other-project", ds.id()))
                .isInstanceOf(ResourceNotFoundException.class);
        // A wrong-project request must not clear the real source's held secret.
        assertThat(credentials.has(ds.id())).isTrue();
    }

    @Test
    void duplicateCreatesNewSourceWithCopyNameDisabledAndStopped() {
        DataSource original = service.create(
                PROJECT, "Pump", "OPC_UA", "MANUAL", "{\"host\":\"plc1\"}", "{\"rate\":500}", null, null, "a");

        DataSource copy = service.duplicate(PROJECT, original.id(), "a");

        assertThat(copy.id()).isNotEqualTo(original.id());
        assertThat(copy.name()).isEqualTo("Pump (copy)");
        assertThat(copy.protocol()).isEqualTo(Protocol.OPC_UA);
        assertThat(copy.basis()).isEqualTo(SourceBasis.MANUAL);
        assertThat(copy.endpoint()).isEqualTo(original.endpoint());
        assertThat(copy.runtimeConfig()).isEqualTo(original.runtimeConfig());
        assertThat(copy.enabled()).isFalse();
        assertThat(copy.runtimeState()).isEqualTo(RuntimeState.STOPPED);
        assertThat(copy.projectId()).isEqualTo(PROJECT);
    }

    @Test
    void duplicateOnMissingSourceThrowsNotFound() {
        assertThatThrownBy(() -> service.duplicate(PROJECT, "no-such-ds", "a"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void duplicateOnWrongProjectThrowsNotFound() {
        DataSource ds = service.create(PROJECT, "Pump", "OPC_UA", "MANUAL", null, null, null, null, "a");
        assertThatThrownBy(() -> service.duplicate("other-project", ds.id(), "a"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void duplicateWithSchemaCopiesSchemaNodes() {
        FakeSchemaRepository schemaRepo = new FakeSchemaRepository(repository);
        DataSourceService svc = new DataSourceService(
                repository,
                new FakeProjectRepository(Set.of(PROJECT)),
                schemaRepo,
                new InMemoryRuntimeController(),
                credentials,
                new ObjectMapper());

        DataSource original = svc.create(PROJECT, "Sensor", "OPC_UA", "SCAN", null, null, null, null, "a");
        SchemaNode node = new SchemaNode("n1", null, "/root/temp", "Temperature",
                com.ainclusive.iotsim.protocolmodel.NodeKind.VARIABLE,
                com.ainclusive.iotsim.protocolmodel.DataType.FLOAT32,
                com.ainclusive.iotsim.protocolmodel.ValueRank.SCALAR,
                com.ainclusive.iotsim.protocolmodel.Access.READ,
                "°C", null);
        schemaRepo.saveNewVersion(original.id(), List.of(node));

        DataSource copy = svc.duplicate(PROJECT, original.id(), "a");

        assertThat(copy.name()).isEqualTo("Sensor (copy)");
        assertThat(copy.schemaId()).isNotNull();
        assertThat(copy.schemaVersion()).isEqualTo(1);
        assertThat(copy.schemaId()).isNotEqualTo(original.schemaId());
    }

    @Test
    void createImportWithInitialSchemaPopulatesSchemaVersion() {
        FakeSchemaRepository schemaRepo = new FakeSchemaRepository(repository);
        DataSourceService svc = new DataSourceService(
                repository,
                new FakeProjectRepository(Set.of(PROJECT)),
                schemaRepo,
                new InMemoryRuntimeController(),
                credentials,
                new ObjectMapper());

        SchemaNode node = new SchemaNode("n1", null, "/root/temp", "Temperature",
                com.ainclusive.iotsim.protocolmodel.NodeKind.VARIABLE,
                com.ainclusive.iotsim.protocolmodel.DataType.FLOAT32,
                com.ainclusive.iotsim.protocolmodel.ValueRank.SCALAR,
                com.ainclusive.iotsim.protocolmodel.Access.READ,
                "°C", null);

        DataSource ds = svc.create(PROJECT, "Sensor", "OPC_UA", "IMPORT", null, null, null, List.of(node), "a");

        assertThat(ds.basis()).isEqualTo(SourceBasis.IMPORT);
        assertThat(ds.schemaId()).isNotNull();
        assertThat(ds.schemaVersion()).isEqualTo(1);
    }

    @Test
    void listPagedThrowsNotFoundForMissingProject() {
        assertThatThrownBy(() -> service.listPaged("no-such-project", null, null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void listPagedEmitsCursorWhenResultsExceedLimit() {
        service.create(PROJECT, "DS-A", "OPC_UA", "MANUAL", null, null, null, null, "it");
        service.create(PROJECT, "DS-B", "OPC_UA", "MANUAL", null, null, null, null, "it");
        service.create(PROJECT, "DS-C", "MODBUS_TCP", "SCAN", null, null, null, null, "it");

        Page<DataSource> page = service.listPaged(PROJECT, null, null, 2);
        assertThat(page.items()).hasSize(2);
        assertThat(page.nextCursor()).isNotNull();
        assertThat(page.limit()).isEqualTo(2);

        Page<DataSource> page2 = service.listPaged(PROJECT, null, page.nextCursor(), 2);
        assertThat(page2.items()).hasSize(1);
        assertThat(page2.nextCursor()).isNull();

        // protocol filter narrows the set
        Page<DataSource> modbusOnly = service.listPaged(PROJECT, "MODBUS_TCP", null, 10);
        assertThat(modbusOnly.items()).hasSize(1);
        assertThat(modbusOnly.items().get(0).name()).isEqualTo("DS-C");
    }

    @Test
    void startRejectsPortHeldByAnotherRunningSource() {
        DataSource a = service.create(PROJECT, "A", "OPC_UA", "MANUAL", null,
                "{\"listenPort\":4840}", null, null, "it");
        DataSource b = service.create(PROJECT, "B", "OPC_UA", "MANUAL", null,
                "{\"listenPort\":4840}", null, null, "it");
        service.start(PROJECT, a.id());   // A now RUNNING on 4840

        assertThatThrownBy(() -> service.start(PROJECT, b.id()))
                .isInstanceOf(PortInUseException.class);
    }

    @Test
    void startAllowsSamePortWhenOtherSourceIsStopped() {
        DataSource a = service.create(PROJECT, "A", "OPC_UA", "MANUAL", null,
                "{\"listenPort\":4840}", null, null, "it");
        DataSource b = service.create(PROJECT, "B", "OPC_UA", "MANUAL", null,
                "{\"listenPort\":4840}", null, null, "it");
        service.start(PROJECT, a.id());
        service.stop(PROJECT, a.id());    // A STOPPED → 4840 free

        service.start(PROJECT, b.id());   // no throw
        assertThat(service.get(PROJECT, b.id()).runtimeState()).isEqualTo(RuntimeState.RUNNING);
    }

    @Test
    void ephemeralPortNeverConflicts() {
        DataSource a = service.create(PROJECT, "A", "OPC_UA", "MANUAL", null, null, null, null, "it");
        DataSource b = service.create(PROJECT, "B", "OPC_UA", "MANUAL", null, null, null, null, "it");
        service.start(PROJECT, a.id());
        service.start(PROJECT, b.id());   // both ephemeral (0) → no throw
        assertThat(service.get(PROJECT, b.id()).runtimeState()).isEqualTo(RuntimeState.RUNNING);
    }

    @Test
    void restartingSameSourceIsNotSelfConflict() {
        DataSource a = service.create(PROJECT, "A", "OPC_UA", "MANUAL", null,
                "{\"listenPort\":4840}", null, null, "it");
        service.start(PROJECT, a.id());
        service.start(PROJECT, a.id());   // same id re-start → no throw
        assertThat(service.get(PROJECT, a.id()).runtimeState()).isEqualTo(RuntimeState.RUNNING);
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
        public List<DataSourceRow> findAll() {
            return List.copyOf(rows);
        }

        @Override
        public List<DataSourceRow> findByProject(String projectId) {
            return rows.stream().filter(r -> r.projectId().equals(projectId)).toList();
        }

        @Override
        public List<DataSourceRow> findByProjectPaged(String projectId, String protocol,
                java.time.OffsetDateTime afterAt, String afterId, int limit) {
            return rows.stream()
                    .filter(r -> r.projectId().equals(projectId))
                    .filter(r -> protocol == null || r.protocol().equals(protocol))
                    .filter(r -> afterAt == null || r.createdAt().isBefore(afterAt)
                            || (r.createdAt().isEqual(afterAt) && r.id().compareTo(afterId) < 0))
                    .sorted(java.util.Comparator.comparing(DataSourceRow::createdAt).reversed()
                            .thenComparing(java.util.Comparator.comparing(DataSourceRow::id).reversed()))
                    .limit(limit)
                    .toList();
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
        public Optional<DataSourceRow> duplicate(String sourceId, String newName, String createdBy) {
            return rows.stream()
                    .filter(r -> r.id().equals(sourceId))
                    .findFirst()
                    .map(source -> {
                        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
                        DataSourceRow copy = new DataSourceRow(
                                "ds-" + (++seq), source.projectId(), newName,
                                source.protocol(), source.basis(), null, null,
                                source.endpoint(), source.runtimeConfig(),
                                false, now, now, createdBy, 0);
                        rows.add(copy);
                        return copy;
                    });
        }

        @Override
        public boolean deleteById(String id) {
            return rows.removeIf(r -> r.id().equals(id));
        }

        void setSchema(String id, String schemaId, int schemaVersion) {
            for (int i = 0; i < rows.size(); i++) {
                DataSourceRow r = rows.get(i);
                if (r.id().equals(id)) {
                    rows.set(i, new DataSourceRow(
                            r.id(), r.projectId(), r.name(), r.protocol(), r.basis(),
                            schemaId, schemaVersion, r.endpoint(), r.runtimeConfig(),
                            r.enabled(), r.createdAt(), r.updatedAt(), r.createdBy(), r.version()));
                    return;
                }
            }
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
        public List<ProjectRow> findAllPaged(String status, java.time.OffsetDateTime afterAt,
                String afterId, int limit) {
            return List.of();
        }

        @Override
        public Optional<ProjectRow> update(String id, String name, String description, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.Optional<com.ainclusive.iotsim.persistence.project.ProjectRow> archive(String id) {
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

    /**
     * A schema repository backed by a map; mirrors what the jOOQ implementation does:
     * {@code saveNewVersion} also updates the data-source row's schemaId/schemaVersion.
     */
    private static final class FakeSchemaRepository implements SchemaRepository {
        private final java.util.Map<String, SchemaWithNodes> byDataSource = new java.util.HashMap<>();
        private final InMemoryDataSourceRepository dsRepo;
        private int seq;

        FakeSchemaRepository(InMemoryDataSourceRepository dsRepo) {
            this.dsRepo = dsRepo;
        }

        @Override
        public Optional<SchemaWithNodes> findCurrent(String dataSourceId) {
            return Optional.ofNullable(byDataSource.get(dataSourceId));
        }

        @Override
        public SchemaWithNodes saveNewVersion(String dataSourceId, List<SchemaNode> nodes) {
            String schemaId = "schema-" + (++seq);
            int version = 1;
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            SchemaWithNodes swn = new SchemaWithNodes(schemaId, dataSourceId, version, now, List.copyOf(nodes));
            byDataSource.put(dataSourceId, swn);
            dsRepo.setSchema(dataSourceId, schemaId, version);
            return swn;
        }
    }
}
