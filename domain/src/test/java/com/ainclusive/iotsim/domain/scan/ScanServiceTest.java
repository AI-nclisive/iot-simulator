package com.ainclusive.iotsim.domain.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.datasource.SourceBasis;
import com.ainclusive.iotsim.domain.schema.Schema;
import com.ainclusive.iotsim.domain.schema.SchemaService;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.project.ProjectRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.platform.runtime.InMemoryRuntimeController;
import com.ainclusive.iotsim.platform.scan.ConnectionTestResult;
import com.ainclusive.iotsim.platform.scan.DiscoveredNode;
import com.ainclusive.iotsim.platform.scan.ScanResult;
import com.ainclusive.iotsim.platform.scan.ScanSpec;
import com.ainclusive.iotsim.platform.scan.ScanStatus;
import com.ainclusive.iotsim.platform.secret.ConnectionCredentials;
import com.ainclusive.iotsim.platform.secret.InMemoryCredentialStore;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Job lifecycle, create-from-scan, and the secrets-never-persisted guarantee (IS-043). */
class ScanServiceTest {

    private static final String PROJECT = "proj-1";

    private RecordingScanner scanner;
    private InMemoryCredentialStore credentials;
    private InMemoryDataSourceRepository dataSourceRepo;
    private InMemorySchemaRepository schemaRepo;
    private ScanService service;

    @BeforeEach
    void setUp() {
        scanner = new RecordingScanner();
        credentials = new InMemoryCredentialStore();
        dataSourceRepo = new InMemoryDataSourceRepository();
        schemaRepo = new InMemorySchemaRepository(dataSourceRepo);
        ProjectRepository projects = new FakeProjectRepository();
        DataSourceService dataSources = new DataSourceService(
                dataSourceRepo, projects, schemaRepo, new InMemoryRuntimeController(), credentials);
        SchemaService schemas = new SchemaService(schemaRepo, dataSourceRepo);
        // Synchronous executor so the async scan completes inline for deterministic asserts.
        service = new ScanService(scanner, projects, dataSources, schemas, Runnable::run);
    }

    @Test
    void testConnectionDelegatesToScanner() {
        scanner.connectionResult = new ConnectionTestResult(ScanStatus.OK, "ok");
        ConnectionTestResult result = service.testConnection(
                PROJECT, "OPC_UA", "opc.tcp://host:4840", ConnectionCredentials.anonymous());
        assertThat(result.ok()).isTrue();
        assertThat(scanner.lastSpec.endpointUrl()).isEqualTo("opc.tcp://host:4840");
    }

    @Test
    void testConnectionUnderMissingProjectThrowsNotFound() {
        assertThatThrownBy(() -> service.testConnection(
                "nope", "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void startScanRunsJobToCompletionAndForwardsSessionCredentials() {
        scanner.scanResult = okResult();
        ScanJob started = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.password("op", "pw"), 10);

        ScanJob done = service.getScan(PROJECT, started.jobId());
        assertThat(done.state()).isEqualTo("OK");
        assertThat(done.result().nodes()).hasSize(3);
        // The secret was handed to the scanner for the scan, but is never stored on the job.
        assertThat(scanner.lastSpec.credentials().secret()).isEqualTo("pw");
        assertThat(done.toString()).doesNotContain("pw");
    }

    @Test
    void startScanRecordsFailedWhenScannerThrows() {
        scanner.failure = new RuntimeException("boom");
        ScanJob started = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);
        assertThat(service.getScan(PROJECT, started.jobId()).state()).isEqualTo("FAILED");
    }

    @Test
    void scanThatIsUnreachableIsReportedNotThrown() {
        scanner.scanResult = ScanResult.failure(ScanStatus.UNREACHABLE, "down");
        ScanJob started = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);
        assertThat(service.getScan(PROJECT, started.jobId()).state()).isEqualTo("UNREACHABLE");
    }

    @Test
    void getScanFromWrongProjectThrowsNotFound() {
        scanner.scanResult = okResult();
        ScanJob started = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);
        assertThatThrownBy(() -> service.getScan("other", started.jobId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createFromScanPersistsBasisScanAndDropsExcludedUnknownNode() {
        scanner.scanResult = okResult();
        ScanJob job = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.password("op", "pw"), 0);

        DataSource created = service.createFromScan(PROJECT, job.jobId(), "Scanned Pump", "{}",
                List.of(new TypeResolution("ns=2;s=x", null, null, null, true)), "alice");

        assertThat(created.basis()).isEqualTo(SourceBasis.SCAN);
        assertThat(created.schemaId()).isNotBlank();
        // The created source carries no scan secret: credentials are not copied over.
        assertThat(created.credentialState().name()).isEqualTo("MISSING");
        assertThat(credentials.has(created.id())).isFalse();

        Schema schema = new SchemaService(schemaRepo, dataSourceRepo).get(PROJECT, created.id());
        // Folder + the FLOAT64 variable persist; the excluded unknown-typed variable is dropped.
        assertThat(schema.nodes()).hasSize(2);
        assertThat(schema.nodes()).noneMatch(n -> "unknownVar".equals(n.name()));
    }

    @Test
    void createFromScanKeepsResolvedUnknownNodeWithAssignedType() {
        scanner.scanResult = okResult();
        ScanJob job = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);

        DataSource created = service.createFromScan(PROJECT, job.jobId(), "Scanned", "{}",
                List.of(new TypeResolution("ns=2;s=x", "INT32", null, null, false)), "alice");

        Schema schema = new SchemaService(schemaRepo, dataSourceRepo).get(PROJECT, created.id());
        assertThat(schema.nodes()).hasSize(3);
        assertThat(schema.nodes())
                .filteredOn(n -> "unknownVar".equals(n.name()))
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.kind()).isEqualTo(NodeKind.VARIABLE);
                    assertThat(n.dataType()).isEqualTo(DataType.INT32);
                    // valueRank/access default from the discovered node when not overridden.
                    assertThat(n.valueRank()).isEqualTo(ValueRank.SCALAR);
                    assertThat(n.access()).isEqualTo(Access.READ);
                });
    }

    @Test
    void createFromScanRejectsUnresolvedUnknownNode() {
        scanner.scanResult = okResult();
        ScanJob job = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);
        assertThatThrownBy(() -> service.createFromScan(PROJECT, job.jobId(), "x", null, List.of(), "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("requiring resolution");
    }

    @Test
    void createFromScanRejectsResolutionTargetingKnownNode() {
        scanner.scanResult = okResult();
        ScanJob job = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);
        // ns=2;s=temp is a known FLOAT64 variable, so it cannot be a resolution target.
        assertThatThrownBy(() -> service.createFromScan(PROJECT, job.jobId(), "x", null,
                List.of(new TypeResolution("ns=2;s=temp", "INT32", null, null, false)), "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not an unknown-typed node");
    }

    @Test
    void createFromScanRejectsRunningJob() {
        // An executor that never runs the task leaves the job RUNNING.
        ScanService pending = new ScanService(
                scanner,
                new FakeProjectRepository(),
                new DataSourceService(dataSourceRepo, new FakeProjectRepository(),
                        new InMemorySchemaRepository(dataSourceRepo), new InMemoryRuntimeController(), credentials),
                new SchemaService(new InMemorySchemaRepository(dataSourceRepo), dataSourceRepo),
                task -> { /* never executes */ });
        ScanJob job = pending.startScan(PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);
        assertThatThrownBy(() -> pending.createFromScan(PROJECT, job.jobId(), "x", null, List.of(), "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createFromScanRejectsScanWithNoUsableNodes() {
        scanner.scanResult = ScanResult.failure(ScanStatus.UNREACHABLE, "down");
        ScanJob job = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);
        assertThatThrownBy(() -> service.createFromScan(PROJECT, job.jobId(), "x", null, List.of(), "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ScanResult okResult() {
        return new ScanResult(ScanStatus.OK, List.of(
                new DiscoveredNode("ns=2;s=plant", null, "Plant", "Plant", "FOLDER",
                        null, null, null, null, null),
                new DiscoveredNode("ns=2;s=temp", "ns=2;s=plant", "Plant/Temp", "Temp", "VARIABLE",
                        "FLOAT64", "SCALAR", "READ", null, null),
                new DiscoveredNode("ns=2;s=x", "ns=2;s=plant", "Plant/unknownVar", "unknownVar", "VARIABLE",
                        null, null, null, null, null)),
                false, 1, "discovered 3 nodes; 1 of unknown type");
    }

    // ---- fakes ----

    private static final class RecordingScanner
            implements com.ainclusive.iotsim.platform.scan.SourceScanner {
        ScanSpec lastSpec;
        ScanResult scanResult = ScanResult.failure(ScanStatus.UNREACHABLE, "unset");
        ConnectionTestResult connectionResult = new ConnectionTestResult(ScanStatus.OK, "ok");
        RuntimeException failure;

        @Override
        public ConnectionTestResult testConnection(ScanSpec spec) {
            this.lastSpec = spec;
            return connectionResult;
        }

        @Override
        public ScanResult scan(ScanSpec spec) {
            this.lastSpec = spec;
            if (failure != null) {
                throw failure;
            }
            return scanResult;
        }
    }

    private static final class FakeProjectRepository implements ProjectRepository {
        @Override
        public Optional<ProjectRow> findById(String id) {
            if (!PROJECT.equals(id)) {
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

    private static final class InMemoryDataSourceRepository implements DataSourceRepository {
        private final List<DataSourceRow> rows = new ArrayList<>();
        private int seq;

        @Override
        public DataSourceRow insert(String projectId, String name, String protocol, String basis,
                String endpointJson, String runtimeConfigJson, String createdBy) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            DataSourceRow row = new DataSourceRow("ds-" + (++seq), projectId, name, protocol, basis,
                    null, null, endpointJson != null ? endpointJson : "{}",
                    runtimeConfigJson != null ? runtimeConfigJson : "{}", false, now, now, createdBy, 0);
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
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            return rows.removeIf(r -> r.id().equals(id));
        }

        /** Mirrors the Jooq schema repo's atomic linking of a new schema version. */
        void linkSchema(String dataSourceId, String schemaId, int version) {
            for (int i = 0; i < rows.size(); i++) {
                DataSourceRow r = rows.get(i);
                if (r.id().equals(dataSourceId)) {
                    rows.set(i, new DataSourceRow(r.id(), r.projectId(), r.name(), r.protocol(), r.basis(),
                            schemaId, version, r.endpoint(), r.runtimeConfig(), r.enabled(),
                            r.createdAt(), OffsetDateTime.now(ZoneOffset.UTC), r.createdBy(), r.version()));
                    return;
                }
            }
        }
    }

    private static final class InMemorySchemaRepository implements SchemaRepository {
        private final InMemoryDataSourceRepository dataSources;
        private final List<SchemaWithNodes> schemas = new ArrayList<>();
        private int seq;

        InMemorySchemaRepository(InMemoryDataSourceRepository dataSources) {
            this.dataSources = dataSources;
        }

        @Override
        public Optional<SchemaWithNodes> findCurrent(String dataSourceId) {
            return dataSources.findById(dataSourceId).map(DataSourceRow::schemaId)
                    .flatMap(schemaId -> schemas.stream().filter(s -> s.id().equals(schemaId)).findFirst());
        }

        @Override
        public SchemaWithNodes saveNewVersion(String dataSourceId, List<SchemaNode> nodes) {
            int version = (int) schemas.stream().filter(s -> s.dataSourceId().equals(dataSourceId)).count() + 1;
            String schemaId = "schema-" + (++seq);
            SchemaWithNodes saved = new SchemaWithNodes(
                    schemaId, dataSourceId, version, OffsetDateTime.now(ZoneOffset.UTC), List.copyOf(nodes));
            schemas.add(saved);
            dataSources.linkSchema(dataSourceId, schemaId, version);
            return saved;
        }
    }
}
