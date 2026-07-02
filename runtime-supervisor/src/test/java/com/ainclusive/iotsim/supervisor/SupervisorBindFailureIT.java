package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Spawns the real packaged OPC UA worker (installDist) and points it at a listen
 * port already held by an external {@link ServerSocket}. The worker cannot bind and
 * returns {@code Ack(ok=false)} on Start; the supervisor must keep the source in the
 * runtime map as {@code ERROR} carrying the bind reason (rather than throwing the
 * failure out to the caller), so {@code state()/health()} report it. See
 * backend-specs/02_WORKER_CONTRACT_AND_IPC.md (IS-127).
 */
class SupervisorBindFailureIT {

    private static final String DIST_PROPERTY = "iotsim.worker.opcua.dist";

    @Test
    void startOnAlreadyBoundPortLeavesSourceInError() throws Exception {
        List<String> command = workerCommandOrSkip();

        Supervisor supervisor = new Supervisor(new ProcessWorkerLauncher(Map.of("OPC_UA", command)));
        // On macOS ServerSocket defaults to SO_REUSEADDR=true, which would let the
        // worker's Netty bind the same port again. Disable REUSEADDR and hold it on the
        // loopback the worker binds, so its bind genuinely fails.
        ServerSocket held = new ServerSocket();
        held.setReuseAddress(false);
        held.bind(new InetSocketAddress("127.0.0.1", 0));
        try {
            int taken = held.getLocalPort();
            RuntimeStartSpec spec = new RuntimeStartSpec("OPC_UA", 0, List.of(), taken);

            // start() must NOT throw: the worker reports the bind failure via Ack(ok=false),
            // the supervisor keeps the source in the map as ERROR with the reason.
            assertThat(supervisor.start("ds-bind", spec)).isEqualTo("ERROR");
            assertThat(supervisor.state("ds-bind")).isEqualTo("ERROR");
            assertThat(supervisor.health("ds-bind").lastError()).isNotNull();
            assertThat(supervisor.health("ds-bind").lastError().reason())
                    .contains(String.valueOf(taken));
        } finally {
            supervisor.stop("ds-bind");
            held.close();
        }
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
}
