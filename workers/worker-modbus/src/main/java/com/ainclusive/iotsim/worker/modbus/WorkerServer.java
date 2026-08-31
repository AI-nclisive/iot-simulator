package com.ainclusive.iotsim.worker.modbus;

import io.grpc.BindableService;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

/**
 * gRPC server bound to loopback only (never exposed externally), per
 * openspec/specs/worker-contract/spec.md. Pass port 0 for an ephemeral port.
 * Mirrors {@code worker-opcua}'s {@code WorkerServer}.
 */
public final class WorkerServer {

    private final Server server;

    public WorkerServer(int port, BindableService service) {
        this.server = NettyServerBuilder
                .forAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), port))
                .addService(service)
                .build();
    }

    public WorkerServer start() throws IOException {
        server.start();
        return this;
    }

    public int port() {
        return server.getPort();
    }

    public void stop() throws InterruptedException {
        server.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }

    public void awaitTermination() throws InterruptedException {
        server.awaitTermination();
    }
}
