package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Spawns the real packaged OPC UA worker (installDist) as a child process via
 * {@link ProcessWorkerLauncher} and drives it through the supervisor. Protocol-
 * agnostic: proves the spawn + {@code Hello/Configure/Start} handshake, that the
 * worker bound its OPC UA listen port, that ApplyValues streams to the live
 * process, and that stop tears everything down. The full neutral→OPC UA read
 * round-trip is verified in the app module. See backend-specs/02 (IS-039).
 */
class OpcUaWorkerSpawnIT {

    private static final String DIST_PROPERTY = "iotsim.worker.opcua.dist";

    @Test
    void spawnsRealWorkerServesAndTearsDown() throws Exception {
        List<String> command = workerCommandOrSkip();

        Supervisor supervisor = new Supervisor(new ProcessWorkerLauncher(Map.of("OPC_UA", command)));
        int listenPort = PortAllocator.freeLoopbackPort();
        RuntimeStartSpec spec = new RuntimeStartSpec("OPC_UA", 1, List.of(variable("temp", "Temperature")), listenPort);

        String stopState;
        try {
            // start() completes the full Hello -> Configure -> Start handshake against
            // the spawned process; it would throw if the worker failed to come up.
            assertThat(supervisor.start("ds1", spec)).isEqualTo("RUNNING");
            assertThat(supervisor.state("ds1")).isEqualTo("RUNNING");

            assertThat(awaitPort(listenPort, true))
                    .as("worker should bind the OPC UA listen port")
                    .isTrue();

            long applied = supervisor.applyValues("ds1", List.of(
                    NeutralValue.good("temp", Instant.now(), 21.5)));
            assertThat(applied).isEqualTo(1);
        } finally {
            // Tear the worker down unconditionally; assert the outcomes afterwards so a
            // failed assertion here cannot mask the leak check below.
            stopState = supervisor.stop("ds1");
        }

        assertThat(stopState).as("stop should report STOPPED").isEqualTo("STOPPED");
        assertThat(awaitPort(listenPort, false))
                .as("listen port should be released after stop")
                .isTrue();
    }

    private static SchemaNode variable(String id, String name) {
        return new SchemaNode(id, null, id, name, NodeKind.VARIABLE,
                DataType.FLOAT64, ValueRank.SCALAR, Access.READ_WRITE, null, null);
    }

    /** Resolves the installDist launcher script, or skips the test if it is absent. */
    private static List<String> workerCommandOrSkip() {
        String dist = System.getProperty(DIST_PROPERTY);
        assumeTrue(dist != null && !dist.isBlank(),
                "set -D" + DIST_PROPERTY + " to the worker-opcua installDist dir to run this IT");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path bin = Path.of(dist, "bin", windows ? "worker-opcua.bat" : "worker-opcua");
        assumeTrue(Files.isRegularFile(bin), "worker launcher not found at " + bin);
        return List.of(bin.toString());
    }

    /** Polls until the loopback port reaches the desired accepting state, up to ~10s. */
    private static boolean awaitPort(int port, boolean shouldAccept) throws InterruptedException {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            if (accepts(port) == shouldAccept) {
                return true;
            }
            Thread.sleep(100);
        }
        return false;
    }

    private static boolean accepts(int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), 500);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}
