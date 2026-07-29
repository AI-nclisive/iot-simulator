package com.ainclusive.iotsim.domain.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.activityevent.ActivityEventService;
import com.ainclusive.iotsim.domain.activityevent.NoOpActivityEventRepository;
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
import com.ainclusive.iotsim.platform.scan.DiscoveredTypeDefinition;
import com.ainclusive.iotsim.platform.scan.ScanResult;
import com.ainclusive.iotsim.platform.scan.ScanSpec;
import com.ainclusive.iotsim.platform.scan.ScanStatus;
import com.ainclusive.iotsim.platform.secret.ConnectionCredentials;
import com.ainclusive.iotsim.platform.secret.InMemoryCredentialStore;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.DataTypeEnumValue;
import com.ainclusive.iotsim.protocolmodel.DataTypeMember;
import com.ainclusive.iotsim.protocolmodel.NativeTypeKind;
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
import tools.jackson.databind.ObjectMapper;

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
                dataSourceRepo, projects, schemaRepo, new InMemoryRuntimeController(), credentials,
                new ObjectMapper(), "localhost",
                new ActivityEventService(new NoOpActivityEventRepository()));
        SchemaService schemas = new SchemaService(schemaRepo, dataSourceRepo, new ObjectMapper());
        // Synchronous executor so the async scan completes inline for deterministic asserts.
        service = new ScanService(scanner, projects, dataSources, schemas, credentials, Runnable::run);
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

        Schema schema = new SchemaService(schemaRepo, dataSourceRepo, new ObjectMapper()).get(PROJECT, created.id());
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

        Schema schema = new SchemaService(schemaRepo, dataSourceRepo, new ObjectMapper()).get(PROJECT, created.id());
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
    void createFromScanPreservesDeclaredBuiltinNodeIdAlongsideExecutableType() {
        scanner.scanResult = new ScanResult(ScanStatus.OK, List.of(
                new DiscoveredNode("ns=2;s=qname", null, "QName", "QName", "VARIABLE",
                        "QUALIFIED_NAME", "SCALAR", "READ", null, null, "ns=0;i=20")),
                false, 1, "discovered QualifiedName");
        ScanJob job = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);

        DataSource created = service.createFromScan(PROJECT, job.jobId(), "Scanned", "{}", List.of(), "alice");

        Schema schema = new SchemaService(schemaRepo, dataSourceRepo, new ObjectMapper()).get(PROJECT, created.id());
        assertThat(schema.nodes()).singleElement().satisfies(node -> {
            assertThat(node.dataType()).isEqualTo(DataType.QUALIFIED_NAME);
            assertThat(node.dataTypeNodeId()).isNull();
            assertThat(node.declaredDataTypeNodeId()).isEqualTo("ns=0;i=20");
        });
    }

    @Test
    void createFromScanPreservesNonNeutralOpcUaDataTypeWithoutResolution() {
        scanner.scanResult = nonNeutralTypeResult();
        ScanJob job = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);

        DataSource created = service.createFromScan(PROJECT, job.jobId(), "x", null, List.of(), "a");

        Schema schema = new SchemaService(schemaRepo, dataSourceRepo, new ObjectMapper()).get(PROJECT, created.id());
        assertThat(schema.nodes())
                .filteredOn(n -> "variantArray".equals(n.name()))
                .singleElement()
                .satisfies(n -> {
                    assertThat(n.dataType()).isNull();
                    assertThat(n.dataTypeNodeId()).isEqualTo("ns=0;i=28");
                    assertThat(n.valueRank()).isEqualTo(ValueRank.ARRAY);
                });
    }

    @Test
    void createFromScanImportsStructuredNativeDataTypeDefinition() {
        scanner.scanResult = new ScanResult(ScanStatus.OK, List.of(
                new DiscoveredNode("ns=2;s=range", null, "Range", "Range", "VARIABLE",
                        null, "SCALAR", "READ", null, null, "ns=0;i=884",
                        List.of(new DataTypeMember("low", DataType.FLOAT64, null),
                                new DataTypeMember("high", DataType.FLOAT64, null)), List.of(), "ns=2;i=5002",
                        NativeTypeKind.STRUCTURE, List.of(), "ServerRange")),
                false, 1, "discovered structured type");
        ScanJob job = service.startScan(PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);

        DataSource created = service.createFromScan(PROJECT, job.jobId(), "Range source", null, List.of(), "a");

        Schema schema = new SchemaService(schemaRepo, dataSourceRepo, new ObjectMapper()).get(PROJECT, created.id());
        assertThat(schema.nodes()).anySatisfy(node -> {
            assertThat(node.kind()).isEqualTo(NodeKind.DATA_TYPE);
            assertThat(node.nodeId()).isEqualTo("ns=0;i=884");
            assertThat(node.name()).isEqualTo("ServerRange");
            assertThat(node.members()).containsExactly(
                    new DataTypeMember("low", DataType.FLOAT64, null),
                    new DataTypeMember("high", DataType.FLOAT64, null));
            assertThat(node.defaultEncodingId()).isEqualTo("ns=2;i=5002");
        });
    }

    @Test
    void createFromScanPersistsCompleteNativeStructureMetadata() {
        DataTypeMember samples = new DataTypeMember(
                "samples", DataType.UINT16, null, ValueRank.ARRAY, List.of(4), true);
        DataTypeMember engineeringUnit = new DataTypeMember(
                "engineeringUnit", null, "ns=2;i=4101", ValueRank.SCALAR, List.of(), false);
        scanner.scanResult = new ScanResult(ScanStatus.OK, List.of(
                new DiscoveredNode("ns=2;s=measurement", null, "Measurement", "Measurement", "VARIABLE",
                        null, "SCALAR", "READ", null, null, "ns=2;i=4001",
                        List.of(samples, engineeringUnit), List.of(), "ns=2;i=5001",
                        NativeTypeKind.STRUCTURE,
                        List.of(new DiscoveredTypeDefinition("ns=2;i=4101", "EngineeringUnit",
                                List.of(new DataTypeMember("code", DataType.INT32, null)), List.of(),
                                "ns=2;i=5101", NativeTypeKind.STRUCTURE)),
                        "MeasurementData")),
                false, 1, "discovered native structure metadata");
        ScanJob job = service.startScan(PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);

        DataSource created = service.createFromScan(PROJECT, job.jobId(), "Measurement source", null, List.of(), "a");

        Schema schema = new SchemaService(schemaRepo, dataSourceRepo, new ObjectMapper()).get(PROJECT, created.id());
        assertThat(schema.nodes())
                .filteredOn(node -> "ns=2;i=4001".equals(node.nodeId()))
                .singleElement()
                .satisfies(node -> {
                    assertThat(node.name()).isEqualTo("MeasurementData");
                    assertThat(node.nativeTypeKind()).isEqualTo(NativeTypeKind.STRUCTURE);
                    assertThat(node.defaultEncodingId()).isEqualTo("ns=2;i=5001");
                    assertThat(node.members()).containsExactly(samples, engineeringUnit);
                });
        assertThat(schema.nodes())
                .filteredOn(node -> "ns=2;i=4101".equals(node.nodeId()))
                .singleElement()
                .satisfies(node -> {
                    assertThat(node.name()).isEqualTo("EngineeringUnit");
                    assertThat(node.nativeTypeKind()).isEqualTo(NativeTypeKind.STRUCTURE);
                    assertThat(node.defaultEncodingId()).isEqualTo("ns=2;i=5101");
                    assertThat(node.members()).containsExactly(new DataTypeMember("code", DataType.INT32, null));
                });
        assertThat(schema.nodes())
                .filteredOn(node -> "Measurement".equals(node.name()))
                .singleElement()
                .satisfies(node -> assertThat(node.dataTypeNodeId()).isEqualTo("ns=2;i=4001"));
    }

    @Test
    void createFromScanPreservesUnresolvedStructuredMemberTypeAsOpaqueDefinition() {
        scanner.scanResult = new ScanResult(ScanStatus.OK, List.of(
                new DiscoveredNode("ns=2;s=value", null, "Value", "Value", "VARIABLE",
                        null, "SCALAR", "READ", null, null, "ns=2;i=1001",
                        List.of(new DataTypeMember("payload", null, "ns=2;i=2002")), List.of(), "ns=2;i=5002")),
                false, 1, "discovered structured type");
        ScanJob job = service.startScan(PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);

        DataSource created = service.createFromScan(PROJECT, job.jobId(), "Nested source", null, List.of(), "a");

        Schema schema = new SchemaService(schemaRepo, dataSourceRepo, new ObjectMapper()).get(PROJECT, created.id());
        assertThat(schema.nodes()).anySatisfy(node -> {
            assertThat(node.kind()).isEqualTo(NodeKind.DATA_TYPE);
            assertThat(node.nodeId()).isEqualTo("ns=2;i=2002");
            assertThat(node.members()).isEmpty();
            assertThat(node.enumValues()).isEmpty();
        });
    }

    @Test
    void createFromScanUpgradesOpaqueMemberTypeWhenItsDeclarationAppearsLater() {
        scanner.scanResult = new ScanResult(ScanStatus.OK, List.of(
                new DiscoveredNode("ns=2;s=outer", null, "Outer", "Outer", "VARIABLE",
                        null, "SCALAR", "READ", null, null, "ns=2;i=1001",
                        List.of(new DataTypeMember("nested", null, "ns=2;i=2002")), List.of(), "ns=2;i=5001"),
                new DiscoveredNode("ns=2;s=nested", null, "Nested", "Nested", "VARIABLE",
                        null, "SCALAR", "READ", null, null, "ns=2;i=2002",
                        List.of(new DataTypeMember("value", DataType.INT32, null)), List.of(), "ns=2;i=5002")),
                false, 2, "discovered nested structures");
        ScanJob job = service.startScan(PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);

        DataSource created = service.createFromScan(PROJECT, job.jobId(), "Nested source", null, List.of(), "a");

        Schema schema = new SchemaService(schemaRepo, dataSourceRepo, new ObjectMapper()).get(PROJECT, created.id());
        assertThat(schema.nodes())
                .filteredOn(node -> "ns=2;i=2002".equals(node.nodeId()))
                .singleElement()
                .satisfies(node -> {
                    assertThat(node.members()).containsExactly(new DataTypeMember("value", DataType.INT32, null));
                    assertThat(node.defaultEncodingId()).isEqualTo("ns=2;i=5002");
                });
    }

    @Test
    void createFromScanImportsTransitiveTypeDefinitionWithoutASecondVariable() {
        scanner.scanResult = new ScanResult(ScanStatus.OK, List.of(
                new DiscoveredNode("ns=2;s=outer", null, "Outer", "Outer", "VARIABLE",
                        null, "SCALAR", "READ", null, null, "ns=2;i=1001",
                        List.of(new DataTypeMember("nested", null, "ns=2;i=2002")), List.of(), "ns=2;i=5001",
                        List.of(new DiscoveredTypeDefinition("ns=2;i=2002", "Nested",
                                List.of(new DataTypeMember("value", DataType.INT32, null)), List.of(), "ns=2;i=5002")))),
                false, 1, "discovered transitive native type");
        ScanJob job = service.startScan(PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);

        DataSource created = service.createFromScan(PROJECT, job.jobId(), "Nested source", null, List.of(), "a");

        Schema schema = new SchemaService(schemaRepo, dataSourceRepo, new ObjectMapper()).get(PROJECT, created.id());
        assertThat(schema.nodes())
                .filteredOn(node -> "ns=2;i=2002".equals(node.nodeId()))
                .singleElement()
                .satisfies(node -> {
                    assertThat(node.name()).isEqualTo("Nested");
                    assertThat(node.members()).containsExactly(new DataTypeMember("value", DataType.INT32, null));
                    assertThat(node.defaultEncodingId()).isEqualTo("ns=2;i=5002");
                });
    }

    @Test
    void createFromScanImportsNativeEnumDefinition() {
        scanner.scanResult = new ScanResult(ScanStatus.OK, List.of(
                new DiscoveredNode("ns=2;s=state", null, "State", "State", "VARIABLE",
                        null, "SCALAR", "READ", null, null, "ns=2;i=1001", List.of(),
                        List.of(new DataTypeEnumValue("Stopped", 0, "Not running"),
                                new DataTypeEnumValue("Running", 1, "Running")))),
                false, 1, "discovered enum type");
        ScanJob job = service.startScan(PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);

        DataSource created = service.createFromScan(PROJECT, job.jobId(), "State source", null, List.of(), "a");

        Schema schema = new SchemaService(schemaRepo, dataSourceRepo, new ObjectMapper()).get(PROJECT, created.id());
        assertThat(schema.nodes()).anySatisfy(node -> {
            assertThat(node.kind()).isEqualTo(NodeKind.DATA_TYPE);
            assertThat(node.nodeId()).isEqualTo("ns=2;i=1001");
            assertThat(node.enumValues()).containsExactly(
                    new DataTypeEnumValue("Stopped", 0, "Not running"),
                    new DataTypeEnumValue("Running", 1, "Running"));
        });
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
    void typeResolutionWithNullNodeIdThrowsIllegalArgumentNotNpe() {
        // A client omitting nodeId must surface as a 400 (IllegalArgumentException), not a 500.
        assertThatThrownBy(() -> new TypeResolution(null, "INT32", null, null, false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nodeId is required");
    }

    @Test
    void progressUpdatesAreVisibleWhileScanIsRunning() throws InterruptedException {
        scanner.scanResult = okResult();
        scanner.awaitBeforeResult = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            ScanService svc = new ScanService(scanner, new FakeProjectRepository(),
                    new DataSourceService(dataSourceRepo, new FakeProjectRepository(),
                            new InMemorySchemaRepository(dataSourceRepo), new InMemoryRuntimeController(),
                            credentials, new ObjectMapper(), "localhost",
                            new ActivityEventService(new NoOpActivityEventRepository())),
                    new SchemaService(new InMemorySchemaRepository(dataSourceRepo), dataSourceRepo,
                            new ObjectMapper()),
                    credentials,
                    pool::execute);
            ScanJob job = svc.startScan(
                    PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);

            // The scanner blocks after emitting SCANNING/3, so the job must still be RUNNING
            // with that progress visible — poll briefly since the worker-pool thread races us.
            ScanJob polled = job;
            for (int i = 0; i < 200 && polled.phase() != com.ainclusive.iotsim.platform.scan.ScanPhase.SCANNING; i++) {
                Thread.sleep(5);
                polled = svc.getScan(PROJECT, job.jobId());
            }
            assertThat(polled.state()).isEqualTo("RUNNING");
            assertThat(polled.phase()).isEqualTo(com.ainclusive.iotsim.platform.scan.ScanPhase.SCANNING);
            assertThat(polled.discoveredSoFar()).isEqualTo(3);

            scanner.awaitBeforeResult.countDown();
            ScanJob done = svc.getScan(PROJECT, job.jobId());
            for (int i = 0; i < 200 && done.isRunning(); i++) {
                Thread.sleep(5);
                done = svc.getScan(PROJECT, job.jobId());
            }
            assertThat(done.state()).isEqualTo("OK");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cancelScanStopsRunningJobAndMarksCancelled() throws InterruptedException {
        scanner.scanResult = okResult();
        scanner.awaitBeforeResult = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            ScanService svc = new ScanService(scanner, new FakeProjectRepository(),
                    new DataSourceService(dataSourceRepo, new FakeProjectRepository(),
                            new InMemorySchemaRepository(dataSourceRepo), new InMemoryRuntimeController(),
                            credentials, new ObjectMapper(), "localhost",
                            new ActivityEventService(new NoOpActivityEventRepository())),
                    new SchemaService(new InMemorySchemaRepository(dataSourceRepo), dataSourceRepo,
                            new ObjectMapper()),
                    credentials,
                    pool::execute);
            ScanJob job = svc.startScan(
                    PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);

            // Wait until the scan is blocked mid-flight (SCANNING) before cancelling.
            ScanJob polled = job;
            for (int i = 0; i < 200 && polled.phase() != com.ainclusive.iotsim.platform.scan.ScanPhase.SCANNING; i++) {
                Thread.sleep(5);
                polled = svc.getScan(PROJECT, job.jobId());
            }
            assertThat(polled.state()).isEqualTo("RUNNING");

            svc.cancelScan(PROJECT, job.jobId());

            ScanJob done = polled;
            for (int i = 0; i < 200 && done.isRunning(); i++) {
                Thread.sleep(5);
                done = svc.getScan(PROJECT, job.jobId());
            }
            assertThat(done.state()).isEqualTo("CANCELLED");
            assertThat(done.result()).isNull();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void cancelScanOnCompletedJobIsANoOp() {
        scanner.scanResult = okResult();
        ScanJob job = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);
        assertThat(service.getScan(PROJECT, job.jobId()).state()).isEqualTo("OK");

        service.cancelScan(PROJECT, job.jobId());

        assertThat(service.getScan(PROJECT, job.jobId()).state()).isEqualTo("OK");
    }

    @Test
    void cancelScanOnMissingJobThrowsNotFound() {
        assertThatThrownBy(() -> service.cancelScan(PROJECT, "nope"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createFromScanRejectsRunningJob() {
        // An executor that never runs the task leaves the job RUNNING.
        ScanService pending = new ScanService(
                scanner,
                new FakeProjectRepository(),
                new DataSourceService(dataSourceRepo, new FakeProjectRepository(),
                        new InMemorySchemaRepository(dataSourceRepo), new InMemoryRuntimeController(), credentials,
                        new ObjectMapper(), "localhost",
                        new ActivityEventService(new NoOpActivityEventRepository())),
                new SchemaService(new InMemorySchemaRepository(dataSourceRepo), dataSourceRepo, new ObjectMapper()),
                credentials,
                task -> { /* never executes */ });
        ScanJob job = pending.startScan(PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);
        assertThatThrownBy(() -> pending.createFromScan(PROJECT, job.jobId(), "x", null, List.of(), "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getScanNodesPagePagesThroughDiscoveredNodes() {
        scanner.scanResult = okResult();
        ScanJob job = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);

        com.ainclusive.iotsim.domain.support.Page<DiscoveredNode> first =
                service.getScanNodesPage(PROJECT, job.jobId(), null, 2);
        assertThat(first.items()).hasSize(2);
        assertThat(first.nextCursor()).isNotNull();

        com.ainclusive.iotsim.domain.support.Page<DiscoveredNode> second =
                service.getScanNodesPage(PROJECT, job.jobId(), first.nextCursor(), 2);
        assertThat(second.items()).hasSize(1);
        assertThat(second.nextCursor()).isNull();
    }

    @Test
    void getScanNodesPageOnMissingJobThrowsNotFound() {
        assertThatThrownBy(() -> service.getScanNodesPage(PROJECT, "nope", null, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getScanNodesPageWithInvalidCursorThrowsBadRequest() {
        scanner.scanResult = okResult();
        ScanJob job = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);
        assertThatThrownBy(() -> service.getScanNodesPage(PROJECT, job.jobId(), "not-a-number", null))
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

    @Test
    void startRescanReusesStoredProtocolEndpointAndCredentials() {
        scanner.scanResult = okResult();
        ScanJob createJob = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);
        DataSource source = service.createFromScan(PROJECT, createJob.jobId(), "Scanned Pump",
                "opc.tcp://real-device:4840", List.of(new TypeResolution("ns=2;s=x", "INT32", null, null, false)),
                "alice");
        // Rescan reuses the real device's own connection, unlike create — so a credential
        // held for this source (put directly, since createFromScan never copies scan secrets
        // onto the created row) must be forwarded to the scanner, not re-collected from the user.
        credentials.put(source.id(), ConnectionCredentials.password("op", "secret"));

        ScanJob rescanJob = service.startRescan(PROJECT, source.id());

        assertThat(service.getScan(PROJECT, rescanJob.jobId()).state()).isEqualTo("OK");
        assertThat(scanner.lastSpec.protocol()).isEqualTo("OPC_UA");
        assertThat(scanner.lastSpec.endpointUrl()).isEqualTo("opc.tcp://real-device:4840");
        assertThat(scanner.lastSpec.credentials().secret()).isEqualTo("secret");
    }

    @Test
    void startRescanRejectsNonScanBasisSource() {
        DataSourceRow other = dataSourceRepo.insert(
                PROJECT, "Manual source", "OPC_UA", "MANUAL", 4840, "opc.tcp://h", "{}", null, "alice");
        assertThatThrownBy(() -> service.startRescan(PROJECT, other.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SCAN-basis");
    }

    @Test
    void startRescanRejectsSourceWithNoRealDeviceEndpoint() {
        DataSourceRow noEndpoint = dataSourceRepo.insert(
                PROJECT, "No endpoint", "OPC_UA", "SCAN", 4840, null, "{}", null, "alice");
        assertThatThrownBy(() -> service.startRescan(PROJECT, noEndpoint.id()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("real-device endpoint");
    }

    @Test
    void applyRescanSavesNewSchemaVersionOnTheExistingSource() {
        scanner.scanResult = okResult();
        ScanJob createJob = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);
        DataSource source = service.createFromScan(PROJECT, createJob.jobId(), "Scanned Pump",
                "opc.tcp://h", List.of(new TypeResolution("ns=2;s=x", "INT32", null, null, false)), "alice");
        int versionBefore = source.schemaVersion();

        // A second scan discovers a changed structure — only the folder and the known
        // variable this time, no unknown-typed node needing resolution.
        scanner.scanResult = new ScanResult(ScanStatus.OK, List.of(
                new DiscoveredNode("ns=2;s=plant", null, "Plant", "Plant", "FOLDER",
                        null, null, null, null, null),
                new DiscoveredNode("ns=2;s=temp", "ns=2;s=plant", "Plant/Temp", "Temp", "VARIABLE",
                        "FLOAT64", "SCALAR", "READ", null, null)),
                false, 1, "discovered 2 nodes");
        ScanJob rescanJob = service.startRescan(PROJECT, source.id());

        DataSource updated = service.applyRescan(PROJECT, source.id(), rescanJob.jobId(), List.of());

        assertThat(updated.id()).isEqualTo(source.id());
        assertThat(updated.schemaVersion()).isGreaterThan(versionBefore);
        Schema schema = new SchemaService(schemaRepo, dataSourceRepo, new ObjectMapper()).get(PROJECT, source.id());
        assertThat(schema.nodes()).hasSize(2);
        assertThat(schema.nodes()).noneMatch(n -> "unknownVar".equals(n.name()));
    }

    @Test
    void applyRescanPreservesNonNeutralOpcUaDataTypeWithoutResolution() {
        scanner.scanResult = nonNeutralTypeResult();
        ScanJob createJob = service.startScan(
                PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);
        DataSource source = service.createFromScan(PROJECT, createJob.jobId(), "Scanned Pump", "opc.tcp://h",
                List.of(), "alice");

        scanner.scanResult = nonNeutralTypeResult();
        ScanJob rescanJob = service.startRescan(PROJECT, source.id());

        DataSource updated = service.applyRescan(PROJECT, source.id(), rescanJob.jobId(), List.of());
        Schema schema = new SchemaService(schemaRepo, dataSourceRepo, new ObjectMapper()).get(PROJECT, updated.id());
        assertThat(schema.nodes()).anySatisfy(n -> assertThat(n.dataTypeNodeId()).isEqualTo("ns=0;i=28"));
    }

    @Test
    void applyRescanRejectsRunningJob() {
        ScanService pending = new ScanService(
                scanner,
                new FakeProjectRepository(),
                new DataSourceService(dataSourceRepo, new FakeProjectRepository(),
                        new InMemorySchemaRepository(dataSourceRepo), new InMemoryRuntimeController(), credentials,
                        new ObjectMapper(), "localhost",
                        new ActivityEventService(new NoOpActivityEventRepository())),
                new SchemaService(new InMemorySchemaRepository(dataSourceRepo), dataSourceRepo, new ObjectMapper()),
                credentials,
                task -> { /* never executes */ });
        ScanJob job = pending.startScan(PROJECT, "OPC_UA", "opc.tcp://h", ConnectionCredentials.anonymous(), 0);

        // The running-job check short-circuits before the target source is even looked up.
        assertThatThrownBy(() -> pending.applyRescan(PROJECT, "ds-does-not-matter", job.jobId(), List.of()))
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

    private static ScanResult nonNeutralTypeResult() {
        return new ScanResult(ScanStatus.OK, List.of(
                new DiscoveredNode("ns=2;s=plant", null, "Plant", "Plant", "FOLDER",
                        null, null, null, null, null),
                new DiscoveredNode("ns=2;s=variantArray", "ns=2;s=plant", "Plant/VariantArray",
                        "variantArray", "VARIABLE", null, "ARRAY", "READ", null, null, "ns=0;i=28")),
                false, 1, "discovered 2 nodes; 1 non-neutral type");
    }

    // ---- fakes ----

    private static final class RecordingScanner
            implements com.ainclusive.iotsim.platform.scan.SourceScanner {
        ScanSpec lastSpec;
        ScanResult scanResult = ScanResult.failure(ScanStatus.UNREACHABLE, "unset");
        ConnectionTestResult connectionResult = new ConnectionTestResult(ScanStatus.OK, "ok");
        RuntimeException failure;
        /** When set, held after emitting progress but before returning the result — lets a test
         *  observe the job mid-flight. */
        java.util.concurrent.CountDownLatch awaitBeforeResult;

        @Override
        public ConnectionTestResult testConnection(ScanSpec spec) {
            this.lastSpec = spec;
            return connectionResult;
        }

        @Override
        public ScanResult scan(ScanSpec spec,
                com.ainclusive.iotsim.platform.scan.ScanProgressListener onProgress) {
            this.lastSpec = spec;
            onProgress.onProgress(com.ainclusive.iotsim.platform.scan.ScanPhase.CONNECTING, 0);
            onProgress.onProgress(com.ainclusive.iotsim.platform.scan.ScanPhase.CONNECTED, 0);
            onProgress.onProgress(com.ainclusive.iotsim.platform.scan.ScanPhase.SCANNING, 3);
            if (awaitBeforeResult != null) {
                try {
                    awaitBeforeResult.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
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
        public List<ProjectRow> findAllPaged(String status, java.time.OffsetDateTime afterAt,
                String afterId, int limit) {
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

        @Override
        public Optional<ProjectRow> archive(String id) {
            throw new UnsupportedOperationException();
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
            DataSourceRow row = new DataSourceRow("ds-" + (++seq), projectId, name, protocol, basis,
                    null, null, simulatorPort, realDeviceEndpoint,
                    runtimeConfigJson != null ? runtimeConfigJson : "{}", securityConfigJson,
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
            return rows.removeIf(r -> r.id().equals(id));
        }

        /** Mirrors the Jooq schema repo's atomic linking of a new schema version. */
        void linkSchema(String dataSourceId, String schemaId, int version) {
            for (int i = 0; i < rows.size(); i++) {
                DataSourceRow r = rows.get(i);
                if (r.id().equals(dataSourceId)) {
                    rows.set(i, new DataSourceRow(r.id(), r.projectId(), r.name(), r.protocol(), r.basis(),
                            schemaId, version, r.simulatorPort(), r.realDeviceEndpoint(), r.runtimeConfig(),
                            r.securityConfig(),
                            r.enabled(), r.createdAt(), OffsetDateTime.now(ZoneOffset.UTC), r.createdBy(),
                            r.version()));
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
