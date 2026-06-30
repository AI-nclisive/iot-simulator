package com.ainclusive.iotsim.supervisor;

import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

/**
 * In-process {@link WorkerLauncher} for supervisor tests: each launch stands up a
 * real loopback gRPC worker so the supervisor exercises the full
 * {@code Hello/Configure/Start} handshake without spawning a process. Tests can
 * simulate an <em>unexpected</em> crash of the latest worker via {@link #crashLast()}.
 */
final class TestWorkerLauncher implements WorkerLauncher {

    /** One launch: its server, the service it exposes, and the exit signal. */
    record Launch(Server server, TestProtocolService service, CompletableFuture<Void> exit) {}

    private final List<Launch> launches = new CopyOnWriteArrayList<>();
    private volatile boolean handshakeBroken;
    private volatile boolean launchFailing;

    /**
     * From now on, launched workers bind their port but expose no service, so the
     * supervisor's {@code Hello} never succeeds and its handshake stays in flight —
     * used to exercise a stop() that races an unfinished restart.
     */
    void setHandshakeBroken(boolean broken) {
        this.handshakeBroken = broken;
    }

    /**
     * From now on, {@code launch(...)} throws immediately, simulating a worker that
     * cannot be spawned — used to exercise admission-permit rollback on a failed launch.
     */
    void setLaunchFailing(boolean failing) {
        this.launchFailing = failing;
    }

    @Override
    public LaunchedWorker launch(String protocol, int controlPort) throws Exception {
        if (launchFailing) {
            throw new IllegalStateException("simulated launch failure");
        }
        TestProtocolService service = new TestProtocolService();
        NettyServerBuilder builder = NettyServerBuilder
                .forAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), controlPort));
        if (!handshakeBroken) {
            builder.addService(service);
        }
        Server server = builder.build().start();
        CompletableFuture<Void> exit = new CompletableFuture<>();
        launches.add(new Launch(server, service, exit));
        return new LaunchedWorker() {
            @Override
            public void close() {
                server.shutdownNow();
                exit.complete(null);
            }

            @Override
            public CompletionStage<Void> onExit() {
                return exit;
            }
        };
    }

    /** Simulates an unexpected crash of the most recently launched worker. */
    void crashLast() {
        Launch last = last();
        last.server.shutdownNow();
        last.exit.complete(null);
    }

    int launchCount() {
        return launches.size();
    }

    Launch last() {
        return launches.get(launches.size() - 1);
    }

    /** True when every worker this launcher created has been torn down (no leak). */
    boolean allServersShutDown() {
        return launches.stream().allMatch(l -> l.server.isShutdown());
    }

    void closeAll() {
        for (Launch l : launches) {
            try {
                l.server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            l.exit.complete(null);
        }
    }
}
