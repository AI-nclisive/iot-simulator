package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.workercontract.v1.ClientEvent;
import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.ainclusive.iotsim.workercontract.v1.Schema;
import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import com.ainclusive.iotsim.workercontract.v1.StartRequest;
import com.ainclusive.iotsim.workercontract.v1.StopRequest;
import com.ainclusive.iotsim.workercontract.v1.StreamRequest;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.junit.jupiter.api.Test;

/**
 * End-to-end check of the worker's {@code ClientEvents} stream (IS-047): with the
 * full gRPC worker server running a real Milo OPC UA endpoint, a protocol client
 * connecting and disconnecting must surface as CONNECTED then DISCONNECTED events
 * on the supervisor-facing stream. Exercises the whole worker vertical — service,
 * {@link ClientEventHub}, server runtime, and the Milo session listener.
 */
class OpcUaClientEventsIT {

    @Test
    void clientConnectAndDisconnectSurfaceAsClientEvents() throws Exception {
        OpcUaProtocolService service = new OpcUaProtocolService();
        WorkerServer server = new WorkerServer(0, service).start();
        ManagedChannel channel =
                ManagedChannelBuilder.forAddress("127.0.0.1", server.port()).usePlaintext().build();
        int opcPort = freePort();
        BlockingQueue<ClientEvent> events = new LinkedBlockingQueue<>();
        OpcUaClient opcClient = null;
        try {
            ProtocolDataSourceGrpc.ProtocolDataSourceBlockingStub blocking =
                    ProtocolDataSourceGrpc.newBlockingStub(channel);
            blocking.configure(ConfigureRequest.newBuilder()
                    .setListenPort(opcPort)
                    .setSchema(Schema.newBuilder().setVersion(1).addNodes(SchemaNodeMsg.newBuilder()
                            .setNodeId("temp").setPath("Temperature").setName("Temperature")
                            .setKind("VARIABLE").setDataType("FLOAT64")))
                    .build());
            blocking.start(StartRequest.getDefaultInstance());

            // Subscribe to the client-events stream and wait until the worker registered it;
            // events are not buffered, so the stream must be open before the client connects.
            ProtocolDataSourceGrpc.ProtocolDataSourceStub async = ProtocolDataSourceGrpc.newStub(channel);
            async.clientEvents(StreamRequest.getDefaultInstance(), collectInto(events));
            awaitUntil(() -> service.openClientEventStreams() > 0);

            String endpointUrl = "opc.tcp://127.0.0.1:" + opcPort + "/iotsim";
            opcClient = OpcUaClient.create(endpointUrl);
            opcClient.connect().get(15, TimeUnit.SECONDS);

            ClientEvent connected = events.poll(10, TimeUnit.SECONDS);
            assertThat(connected).isNotNull();
            assertThat(connected.getKind()).isEqualTo(ClientEvent.Kind.CONNECTED);
            assertThat(connected.getClientId()).isNotEmpty();
            assertThat(connected.getAtMicros()).isPositive();

            opcClient.disconnect().get(10, TimeUnit.SECONDS);
            opcClient = null;

            ClientEvent disconnected = events.poll(10, TimeUnit.SECONDS);
            assertThat(disconnected).isNotNull();
            assertThat(disconnected.getKind()).isEqualTo(ClientEvent.Kind.DISCONNECTED);
        } finally {
            if (opcClient != null) {
                opcClient.disconnect().get(10, TimeUnit.SECONDS);
            }
            ProtocolDataSourceGrpc.newBlockingStub(channel).stop(StopRequest.getDefaultInstance());
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.stop();
        }
    }

    private static StreamObserver<ClientEvent> collectInto(BlockingQueue<ClientEvent> events) {
        return new StreamObserver<>() {
            @Override
            public void onNext(ClientEvent event) {
                events.add(event);
            }

            @Override
            public void onError(Throwable t) {
                // stream cancelled on teardown; nothing to do
            }

            @Override
            public void onCompleted() {
            }
        };
    }

    private static void awaitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("condition not met within timeout");
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            return socket.getLocalPort();
        }
    }
}
