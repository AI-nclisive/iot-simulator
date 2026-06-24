package com.epam.iotsim.supervisor;

import static org.assertj.core.api.Assertions.assertThat;

import com.epam.iotsim.platform.runtime.RuntimeStartSpec;
import com.epam.iotsim.protocolmodel.NeutralValue;
import com.epam.iotsim.protocolmodel.NodeKind;
import com.epam.iotsim.protocolmodel.SchemaNode;
import com.epam.iotsim.workercontract.WorkerContract;
import com.epam.iotsim.workercontract.v1.Ack;
import com.epam.iotsim.workercontract.v1.ConfigureRequest;
import com.epam.iotsim.workercontract.v1.HelloRequest;
import com.epam.iotsim.workercontract.v1.HelloResponse;
import com.epam.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.epam.iotsim.workercontract.v1.StartRequest;
import com.epam.iotsim.workercontract.v1.StopRequest;
import com.epam.iotsim.workercontract.v1.ValueBatch;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Exercises the supervisor end to end with an in-process worker launcher. */
class SupervisorTest {

    private final List<Server> servers = new CopyOnWriteArrayList<>();
    private final List<TestProtocolService> services = new CopyOnWriteArrayList<>();

    private WorkerLauncher inProcessLauncher() {
        return (protocol, controlPort) -> {
            TestProtocolService service = new TestProtocolService();
            services.add(service);
            Server server = NettyServerBuilder
                    .forAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), controlPort))
                    .addService(service)
                    .build()
                    .start();
            servers.add(server);
            return () -> server.shutdownNow();
        };
    }

    private static RuntimeStartSpec spec(SchemaNode... nodes) {
        return new RuntimeStartSpec("OPC_UA", 1, List.of(nodes), 0);
    }

    private static SchemaNode folder(String id) {
        return new SchemaNode(id, null, id, id, NodeKind.FOLDER, null, null, null, null, null);
    }

    @AfterEach
    void tearDown() {
        for (Server server : servers) {
            try {
                server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Test
    void startLaunchesWorkerAndStopTearsItDown() {
        Supervisor supervisor = new Supervisor(inProcessLauncher());

        assertThat(supervisor.start("ds1", spec())).isEqualTo("RUNNING");
        assertThat(supervisor.state("ds1")).isEqualTo("RUNNING");
        assertThat(servers).hasSize(1);

        assertThat(supervisor.stop("ds1")).isEqualTo("STOPPED");
        assertThat(supervisor.state("ds1")).isEqualTo("STOPPED");
    }

    @Test
    void startIsIdempotentForSameSource() {
        Supervisor supervisor = new Supervisor(inProcessLauncher());
        supervisor.start("ds1", spec());
        supervisor.start("ds1", spec());
        assertThat(servers).hasSize(1);
    }

    @Test
    void stateOfUnknownSourceIsStopped() {
        Supervisor supervisor = new Supervisor(inProcessLauncher());
        assertThat(supervisor.state("unknown")).isEqualTo("STOPPED");
    }

    @Test
    void configurePropagatesSchemaToWorker() {
        Supervisor supervisor = new Supervisor(inProcessLauncher());
        supervisor.start("ds1", spec(folder("a"), folder("b")));
        assertThat(services).hasSize(1);
        assertThat(services.get(0).configuredNodeCount()).isEqualTo(2);
    }

    @Test
    void applyValuesStreamsToRunningWorker() {
        Supervisor supervisor = new Supervisor(inProcessLauncher());
        supervisor.start("ds1", spec());

        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        long applied = supervisor.applyValues("ds1", List.of(
                NeutralValue.good("temp", t, 21.5),
                NeutralValue.good("temp", t.plusMillis(1), 21.6)));

        assertThat(applied).isEqualTo(2);
        assertThat(services).hasSize(1);
        assertThat(services.get(0).appliedCount()).isEqualTo(2);
    }

    private static final class TestProtocolService
            extends ProtocolDataSourceGrpc.ProtocolDataSourceImplBase {

        private final AtomicLong applied = new AtomicLong();
        private final AtomicInteger configuredNodes = new AtomicInteger();

        long appliedCount() {
            return applied.get();
        }

        int configuredNodeCount() {
            return configuredNodes.get();
        }

        @Override
        public void hello(HelloRequest request, StreamObserver<HelloResponse> obs) {
            obs.onNext(HelloResponse.newBuilder()
                    .setContractVersion(WorkerContract.VERSION)
                    .setProtocol("TEST")
                    .build());
            obs.onCompleted();
        }

        @Override
        public void configure(ConfigureRequest request, StreamObserver<Ack> obs) {
            configuredNodes.set(request.getSchema().getNodesCount());
            ackOk(obs);
        }

        @Override
        public void start(StartRequest request, StreamObserver<Ack> obs) {
            ackOk(obs);
        }

        @Override
        public void stop(StopRequest request, StreamObserver<Ack> obs) {
            ackOk(obs);
        }

        @Override
        public StreamObserver<ValueBatch> applyValues(StreamObserver<Ack> responseObserver) {
            return new StreamObserver<>() {
                @Override
                public void onNext(ValueBatch batch) {
                    applied.addAndGet(batch.getValuesCount());
                }

                @Override
                public void onError(Throwable t) {
                }

                @Override
                public void onCompleted() {
                    ackOk(responseObserver);
                }
            };
        }

        private static void ackOk(StreamObserver<Ack> obs) {
            obs.onNext(Ack.newBuilder().setOk(true).build());
            obs.onCompleted();
        }
    }
}
