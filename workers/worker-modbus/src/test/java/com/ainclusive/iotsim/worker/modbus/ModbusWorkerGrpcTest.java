package com.ainclusive.iotsim.worker.modbus;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.workercontract.WorkerContract;
import com.ainclusive.iotsim.workercontract.v1.Ack;
import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.HealthRequest;
import com.ainclusive.iotsim.workercontract.v1.HelloRequest;
import com.ainclusive.iotsim.workercontract.v1.HelloResponse;
import com.ainclusive.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.ainclusive.iotsim.workercontract.v1.Schema;
import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import com.ainclusive.iotsim.workercontract.v1.StartRequest;
import com.ainclusive.iotsim.workercontract.v1.StopRequest;
import com.ainclusive.iotsim.workercontract.v1.Value;
import com.ainclusive.iotsim.workercontract.v1.ValueBatch;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Drives the worker's gRPC server over a real loopback connection. Mirrors
 * {@code OpcUaWorkerGrpcTest}. Every {@code listenPort} here is explicit and
 * non-privileged: unlike Milo, j2mod's TCP listener treats port 0 as "use the
 * Modbus default port 502" (a privileged port), not "pick an ephemeral port".
 */
class ModbusWorkerGrpcTest {

    private static ModbusProtocolService service;
    private static WorkerServer server;
    private static ManagedChannel channel;
    private static ProtocolDataSourceGrpc.ProtocolDataSourceBlockingStub stub;

    @BeforeAll
    static void startServer() throws Exception {
        service = new ModbusProtocolService();
        server = new WorkerServer(0, service).start();
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.port()).usePlaintext().build();
        stub = ProtocolDataSourceGrpc.newBlockingStub(channel);
    }

    @AfterAll
    static void stopServer() throws Exception {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.stop();
    }

    @Test
    void helloReportsContractVersionAndProtocol() {
        HelloResponse hello = stub.hello(
                HelloRequest.newBuilder().setContractVersion(WorkerContract.VERSION).build());
        assertThat(hello.getContractVersion()).isEqualTo(WorkerContract.VERSION);
        assertThat(hello.getProtocol()).isEqualTo("MODBUS_TCP");
        assertThat(hello.getCapabilitiesList()).contains("FLOAT32", "BOOL");
    }

    @Test
    void lifecycleTransitionsAreReflectedInHealth() {
        stub.configure(ConfigureRequest.newBuilder().setListenPort(48501).build());
        stub.start(StartRequest.getDefaultInstance());
        assertThat(stub.health(HealthRequest.getDefaultInstance()).getState()).isEqualTo("RUNNING");

        stub.stop(StopRequest.getDefaultInstance());
        assertThat(stub.health(HealthRequest.getDefaultInstance()).getState()).isEqualTo("STOPPED");
    }

    @Test
    void configureStoresSchemaNodeCount() {
        stub.configure(ConfigureRequest.newBuilder()
                .setSchema(Schema.newBuilder()
                        .setVersion(1)
                        .addNodes(SchemaNodeMsg.newBuilder()
                                .setNodeId("a").setPath("A").setName("A").setKind("FOLDER").build())
                        .addNodes(SchemaNodeMsg.newBuilder()
                                .setNodeId("b").setPath("B").setName("B").setKind("FOLDER").build()))
                .setListenPort(48502)
                .build());
        assertThat(service.configuredNodeCount()).isEqualTo(2);
    }

    @Test
    void startReturnsNotOkWhenPortIsAlreadyBound() throws Exception {
        java.net.ServerSocket held = new java.net.ServerSocket();
        held.setReuseAddress(false);
        held.bind(new java.net.InetSocketAddress("127.0.0.1", 0));
        try {
            int taken = held.getLocalPort();
            stub.configure(ConfigureRequest.newBuilder().setListenPort(taken).build());
            Ack ack = stub.start(StartRequest.newBuilder().build());
            assertThat(ack.getOk()).isFalse();
            assertThat(ack.getMessage()).contains(String.valueOf(taken));
        } finally {
            held.close();
        }
    }

    @Test
    void configureWithNetworkOptionsStarts() {
        stub.configure(ConfigureRequest.newBuilder()
                .setListenPort(48504)
                .putOptions("bindAddress", "127.0.0.1")
                .putOptions("unitId", "1")
                .build());
        Ack started = stub.start(StartRequest.newBuilder().build());
        assertThat(started.getOk()).isTrue();
        stub.stop(StopRequest.getDefaultInstance());
    }

    @Test
    void applyValuesIsReceivedAndCounted() throws Exception {
        ProtocolDataSourceGrpc.ProtocolDataSourceStub async = ProtocolDataSourceGrpc.newStub(channel);
        CountDownLatch done = new CountDownLatch(1);
        StreamObserver<ValueBatch> request = async.applyValues(new StreamObserver<>() {
            @Override
            public void onNext(Ack ack) {
            }

            @Override
            public void onError(Throwable t) {
                done.countDown();
            }

            @Override
            public void onCompleted() {
                done.countDown();
            }
        });
        request.onNext(ValueBatch.newBuilder()
                .addValues(Value.newBuilder().setNodeId("a").build())
                .addValues(Value.newBuilder().setNodeId("b").build())
                .build());
        request.onCompleted();

        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(service.appliedCount()).isGreaterThanOrEqualTo(2);
    }
}
