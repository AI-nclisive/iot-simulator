package com.ainclusive.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.workercontract.WorkerContract;
import com.ainclusive.iotsim.workercontract.v1.Ack;
import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.HealthRequest;
import com.ainclusive.iotsim.workercontract.v1.HealthResponse;
import com.ainclusive.iotsim.workercontract.v1.HelloRequest;
import com.ainclusive.iotsim.workercontract.v1.HelloResponse;
import com.ainclusive.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.ainclusive.iotsim.workercontract.v1.Schema;
import com.ainclusive.iotsim.workercontract.v1.SecurityConfig;
import com.ainclusive.iotsim.workercontract.v1.StartRequest;
import com.ainclusive.iotsim.workercontract.v1.StopRequest;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WorkerClientTest {

    private Server server;

    private int startServer(String contractVersion) throws IOException {
        server = NettyServerBuilder
                .forAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
                .addService(new TestProtocolService(contractVersion))
                .build()
                .start();
        return server.getPort();
    }

    @AfterEach
    void stopServer() throws Exception {
        if (server != null) {
            server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void helloSucceedsForCompatibleMajorVersion() throws Exception {
        int port = startServer("1.4.2");
        try (WorkerClient client = new WorkerClient("127.0.0.1", port)) {
            HelloResponse hello = client.hello();
            assertThat(hello.getProtocol()).isEqualTo("TEST");
        }
    }

    @Test
    void lifecycleRoundTripReflectsHealth() throws Exception {
        int port = startServer(WorkerContract.VERSION);
        try (WorkerClient client = new WorkerClient("127.0.0.1", port)) {
            client.configure(Schema.getDefaultInstance(), 48400, "127.0.0.1", "127.0.0.1",
                    SecurityConfig.getDefaultInstance());
            client.start();
            assertThat(client.health().getState()).isEqualTo("RUNNING");
            client.stop();
            assertThat(client.health().getState()).isEqualTo("STOPPED");
        }
    }

    @Test
    void mismatchedMajorVersionIsRefused() throws Exception {
        int port = startServer("2.0.0");
        try (WorkerClient client = new WorkerClient("127.0.0.1", port)) {
            assertThatThrownBy(client::hello).isInstanceOf(WorkerContractMismatchException.class);
        }
    }

    /** Minimal worker stand-in returning a configurable contract version. */
    private static final class TestProtocolService
            extends ProtocolDataSourceGrpc.ProtocolDataSourceImplBase {

        private final String contractVersion;
        private final AtomicReference<String> state = new AtomicReference<>("READY");

        TestProtocolService(String contractVersion) {
            this.contractVersion = contractVersion;
        }

        @Override
        public void hello(HelloRequest request, StreamObserver<HelloResponse> obs) {
            obs.onNext(HelloResponse.newBuilder()
                    .setContractVersion(contractVersion)
                    .setProtocol("TEST")
                    .build());
            obs.onCompleted();
        }

        @Override
        public void configure(ConfigureRequest request, StreamObserver<Ack> obs) {
            state.set("CONFIGURED");
            ackOk(obs);
        }

        @Override
        public void start(StartRequest request, StreamObserver<Ack> obs) {
            state.set("RUNNING");
            ackOk(obs);
        }

        @Override
        public void stop(StopRequest request, StreamObserver<Ack> obs) {
            state.set("STOPPED");
            ackOk(obs);
        }

        @Override
        public void health(HealthRequest request, StreamObserver<HealthResponse> obs) {
            obs.onNext(HealthResponse.newBuilder().setLive(true).setState(state.get()).build());
            obs.onCompleted();
        }

        private static void ackOk(StreamObserver<Ack> obs) {
            obs.onNext(Ack.newBuilder().setOk(true).build());
            obs.onCompleted();
        }
    }
}
