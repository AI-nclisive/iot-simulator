package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ainclusive.iotsim.api.datasource.DataSourceController.DataSourceResponse;
import com.ainclusive.iotsim.api.scan.ScanController;
import com.ainclusive.iotsim.api.scan.ScanController.CreateFromScanRequest;
import com.ainclusive.iotsim.api.scan.ScanController.ScanJobResponse;
import com.ainclusive.iotsim.api.scan.ScanController.ScanRequest;
import com.ainclusive.iotsim.api.support.ConnectionConfigRequest;
import com.ainclusive.iotsim.domain.datasource.CredentialState;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.Protocol;
import com.ainclusive.iotsim.domain.datasource.RuntimeState;
import com.ainclusive.iotsim.domain.datasource.SourceBasis;
import com.ainclusive.iotsim.domain.scan.ScanJob;
import com.ainclusive.iotsim.domain.scan.ScanService;
import com.ainclusive.iotsim.platform.scan.ConnectionTestResult;
import com.ainclusive.iotsim.platform.scan.DiscoveredNode;
import com.ainclusive.iotsim.platform.scan.ScanResult;
import com.ainclusive.iotsim.platform.scan.ScanStatus;
import com.ainclusive.iotsim.platform.secret.ConnectionCredentials;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

/** Unit test for {@link ScanController}. */
class ScanControllerTest {

    private static final String PROJECT = "proj-1";

    private ScanService service;
    private ScanController controller;

    @BeforeEach
    void setUp() {
        service = mock(ScanService.class);
        controller = new ScanController(service);
    }

    @Test
    void testConnectionReturnsStatusAndForwardsCredentialsWithoutEchoingSecret() {
        given(service.testConnection(eq(PROJECT), eq("OPC_UA"), eq("opc.tcp://h"), any()))
                .willReturn(new ConnectionTestResult(ScanStatus.OK, "connected"));
        var cfg = new ConnectionConfigRequest("password", "operator", "s3cr3t", null);

        var resp = controller.testConnection(PROJECT,
                new ScanRequest("OPC_UA", "opc.tcp://h", null, cfg));

        assertThat(resp.status()).isEqualTo("OK");
        ArgumentCaptor<ConnectionCredentials> creds = ArgumentCaptor.forClass(ConnectionCredentials.class);
        verify(service).testConnection(eq(PROJECT), eq("OPC_UA"), eq("opc.tcp://h"), creds.capture());
        assertThat(creds.getValue().secret()).isEqualTo("s3cr3t");
        // The response carries no secret field; its toString cannot leak one.
        assertThat(resp.toString()).doesNotContain("s3cr3t");
    }

    @Test
    void testConnectionWithBlankEndpointThrowsBadRequest() {
        assertThatThrownBy(() -> controller.testConnection(PROJECT,
                new ScanRequest("OPC_UA", " ", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startScanReturns202WithJobIdAndLocation() {
        given(service.startScan(eq(PROJECT), eq("OPC_UA"), eq("opc.tcp://h"), any(), eq(50)))
                .willReturn(running("job-1"));

        ResponseEntity<ScanController.StartScanResponse> resp = controller.startScan(PROJECT,
                new ScanRequest("OPC_UA", "opc.tcp://h", 50, null));

        assertThat(resp.getStatusCode().value()).isEqualTo(202);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().jobId()).isEqualTo("job-1");
        assertThat(resp.getBody().status()).isEqualTo("RUNNING");
        assertThat(resp.getHeaders().getLocation())
                .hasToString("/api/v1/projects/proj-1/data-sources/scan/job-1");
    }

    @Test
    void getMapsCompletedJobIncludingDiscoveredNodes() {
        ScanResult result = new ScanResult(ScanStatus.PARTIAL, List.of(
                new DiscoveredNode("ns=2;s=t", null, "Temp", "Temp", "VARIABLE",
                        "FLOAT64", "SCALAR", "READ", null, null)),
                true, 2, "partial");
        given(service.getScan(PROJECT, "job-1")).willReturn(
                new ScanJob("job-1", PROJECT, "OPC_UA", "opc.tcp://h", "PARTIAL", result,
                        "partial", Instant.now(), Instant.now()));

        ScanJobResponse resp = controller.get(PROJECT, "job-1");

        assertThat(resp.status()).isEqualTo("PARTIAL");
        assertThat(resp.truncated()).isTrue();
        assertThat(resp.unknownCount()).isEqualTo(2);
        assertThat(resp.nodes()).singleElement()
                .satisfies(n -> assertThat(n.dataType()).isEqualTo("FLOAT64"));
    }

    @Test
    void createReturns201WithEtagAndScanBasis() {
        given(service.createFromScan(eq(PROJECT), eq("job-1"), eq("Scanned"), any(), any()))
                .willReturn(scanned());

        ResponseEntity<DataSourceResponse> resp = controller.create(PROJECT, "job-1",
                new CreateFromScanRequest("Scanned", "{}"));

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().basis()).isEqualTo("SCAN");
    }

    @Test
    void createWithBlankNameThrowsBadRequest() {
        assertThatThrownBy(() -> controller.create(PROJECT, "job-1",
                new CreateFromScanRequest(" ", null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ScanJob running(String jobId) {
        Instant now = Instant.now();
        return new ScanJob(jobId, PROJECT, "OPC_UA", "opc.tcp://h", "RUNNING", null,
                "scan in progress", now, now);
    }

    private static DataSource scanned() {
        Instant now = Instant.now();
        return new DataSource("ds1", PROJECT, "Scanned", Protocol.OPC_UA, SourceBasis.SCAN,
                "schema-1", 1, "{}", "{}", false, RuntimeState.STOPPED, CredentialState.MISSING,
                now, now, "local", 0);
    }
}
