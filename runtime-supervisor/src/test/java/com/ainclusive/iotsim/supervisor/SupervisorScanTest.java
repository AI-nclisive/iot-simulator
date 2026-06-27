package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.platform.scan.ConnectionTestResult;
import com.ainclusive.iotsim.platform.scan.ScanResult;
import com.ainclusive.iotsim.platform.scan.ScanSpec;
import com.ainclusive.iotsim.platform.scan.ScanStatus;
import com.ainclusive.iotsim.platform.secret.ConnectionCredentials;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Verifies create-from-scan orchestration: spawn a one-shot worker, scan, tear down. */
class SupervisorScanTest {

    private final TestWorkerLauncher launcher = new TestWorkerLauncher();
    private Supervisor supervisor;

    private Supervisor supervisor() {
        supervisor = new Supervisor(launcher);
        return supervisor;
    }

    @AfterEach
    void tearDown() {
        if (supervisor != null) {
            supervisor.close();
        }
        launcher.closeAll();
    }

    @Test
    void scanSpawnsWorkerMapsResultAndTearsDown() {
        Supervisor supervisor = supervisor();

        ScanResult result = supervisor.scan(new ScanSpec(
                "OPC_UA", "opc.tcp://host:4840/x", ConnectionCredentials.anonymous(), 0));

        assertThat(result.status()).isEqualTo(ScanStatus.OK);
        assertThat(result.nodes()).hasSize(1);
        assertThat(result.nodes().get(0).name()).isEqualTo("Temperature");
        assertThat(result.nodes().get(0).dataType()).isEqualTo("FLOAT64");
        // The one-shot worker was launched and then torn down (not leaked, not adopted).
        assertThat(launcher.launchCount()).isEqualTo(1);
        assertThat(supervisor.state("opc.tcp://host:4840/x")).isEqualTo("STOPPED");
        assertThat(launcher.allServersShutDown()).isTrue();
    }

    @Test
    void scanForwardsEndpointAndSessionCredentialsToWorker() {
        Supervisor supervisor = supervisor();

        supervisor.scan(new ScanSpec(
                "OPC_UA", "opc.tcp://plant:4840/ua",
                ConnectionCredentials.password("operator", "s3cret"), 100));

        var sent = launcher.last().service().lastScanRequest();
        assertThat(sent.getEndpointUrl()).isEqualTo("opc.tcp://plant:4840/ua");
        assertThat(sent.getMaxNodes()).isEqualTo(100);
        assertThat(sent.getCredentials().getMode()).isEqualTo("PASSWORD");
        assertThat(sent.getCredentials().getUsername()).isEqualTo("operator");
        assertThat(sent.getCredentials().getSecret()).isEqualTo("s3cret");
    }

    @Test
    void testConnectionSpawnsWorkerAndReportsOk() {
        Supervisor supervisor = supervisor();

        ConnectionTestResult result = supervisor.testConnection(new ScanSpec(
                "OPC_UA", "opc.tcp://host:4840/x", ConnectionCredentials.anonymous(), 0));

        assertThat(result.ok()).isTrue();
        assertThat(launcher.last().service().lastTestConnectionRequest().getEndpointUrl())
                .isEqualTo("opc.tcp://host:4840/x");
        assertThat(launcher.allServersShutDown()).isTrue();
    }

    @Test
    void scanRejectsUnsupportedProtocolWithoutLaunching() {
        Supervisor supervisor = supervisor();

        ScanResult result = supervisor.scan(new ScanSpec(
                "MODBUS_TCP", "tcp://host:502", ConnectionCredentials.anonymous(), 0));

        assertThat(result.status()).isEqualTo(ScanStatus.UNSUPPORTED);
        assertThat(launcher.launchCount()).isZero();
    }
}
