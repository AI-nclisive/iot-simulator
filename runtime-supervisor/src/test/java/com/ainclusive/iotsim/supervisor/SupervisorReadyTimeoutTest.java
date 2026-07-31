package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Regression test for #694: a control port that accepts a TCP connection but never speaks
 * gRPC (e.g. something else briefly holding the port the supervisor just handed the child, or
 * a process stuck mid-startup before its gRPC service is registered) must not defeat
 * {@code Supervisor}'s ready-wait bound. Before the fix, {@code WorkerClient.hello()} had no
 * deadline of its own, so a single stuck attempt inside {@code awaitReady}'s retry loop could
 * block for as long as the underlying gRPC channel took to notice the peer was unresponsive —
 * far longer than the intended ~10s {@code READY_TIMEOUT}, and indistinguishable from a worker
 * that "never launches" (no exception, no progress, nothing to show for it).
 */
class SupervisorReadyTimeoutTest {

    /**
     * Bound the READY_TIMEOUT is documented as ~10s; this asserts the failure surfaces in
     * well under a minute, which the unfixed code could not guarantee (a single hung Hello
     * attempt was unbounded).
     */
    private static final long MAX_ACCEPTABLE_MILLIS = 20_000;

    private SilentSocketLauncher launcher;
    private Supervisor supervisor;

    @AfterEach
    void tearDown() {
        if (supervisor != null) {
            supervisor.close();
        }
        if (launcher != null) {
            launcher.closeAll();
        }
    }

    @Test
    void startFailsInBoundedTimeWhenControlPortAcceptsButNeverSpeaksTheProtocol() {
        launcher = new SilentSocketLauncher();
        supervisor = new Supervisor(launcher);
        RuntimeStartSpec spec = new RuntimeStartSpec("OPC_UA", 0, List.of(), 0);

        long startNanos = System.nanoTime();
        assertThatThrownBy(() -> supervisor.start("ds1", spec))
                .isInstanceOf(WorkerLaunchException.class)
                .hasMessageContaining("worker did not become ready in time");
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;

        assertThat(elapsedMs)
                .as("a worker stuck accepting-but-silent must fail within the ready-wait bound,"
                        + " not hang indefinitely")
                .isLessThan(MAX_ACCEPTABLE_MILLIS);
    }

    /** A {@link WorkerLauncher} that binds the control port but never answers any protocol. */
    private static final class SilentSocketLauncher implements WorkerLauncher {
        private final List<ServerSocket> sockets = new CopyOnWriteArrayList<>();

        @Override
        public LaunchedWorker launch(String protocol, int controlPort) throws IOException {
            ServerSocket socket = new ServerSocket();
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), controlPort));
            sockets.add(socket);
            // Accept connections in the background so the peer sees an established TCP
            // connection (not "connection refused"), then simply never write anything back.
            Thread acceptor = new Thread(() -> {
                try {
                    while (!socket.isClosed()) {
                        Socket accepted = socket.accept();
                        // Deliberately never read/write: the RPC never completes.
                        Thread.sleep(60_000);
                        accepted.close();
                    }
                } catch (Exception ignored) {
                    // socket closed during teardown, or the sleep was interrupted; either way done
                }
            }, "silent-worker-acceptor");
            acceptor.setDaemon(true);
            acceptor.start();

            CompletableFuture<Void> exit = new CompletableFuture<>();
            return new LaunchedWorker() {
                @Override
                public void close() {
                    closeQuietly(socket);
                    exit.complete(null);
                }

                @Override
                public CompletionStage<Void> onExit() {
                    return exit;
                }
            };
        }

        void closeAll() {
            sockets.forEach(SilentSocketLauncher::closeQuietly);
        }

        private static void closeQuietly(ServerSocket socket) {
            try {
                socket.close();
            } catch (IOException ignored) {
                // best effort
            }
        }
    }
}
