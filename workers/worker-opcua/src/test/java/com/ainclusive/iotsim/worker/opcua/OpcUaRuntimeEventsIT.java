package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.workercontract.v1.Ack;
import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.ainclusive.iotsim.workercontract.v1.RuntimeEvent;
import com.ainclusive.iotsim.workercontract.v1.Schema;
import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import com.ainclusive.iotsim.workercontract.v1.StartRequest;
import com.ainclusive.iotsim.workercontract.v1.StopRequest;
import com.ainclusive.iotsim.workercontract.v1.StreamRequest;
import com.ainclusive.iotsim.workercontract.v1.Value;
import com.ainclusive.iotsim.workercontract.v1.ValueBatch;
import com.google.protobuf.ByteString;
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
import org.junit.jupiter.api.Test;

/**
 * End-to-end check of the worker's {@code RuntimeEvents} stream (IS-048): with the
 * full gRPC worker server running, starting the server surfaces SOURCE_START, a
 * value that cannot be applied surfaces ERROR, and stopping surfaces SOURCE_STOP.
 * Exercises the whole worker vertical — service, {@link RuntimeEventHub}, and the
 * server runtime. The stream is subscribed before Start because events are not
 * buffered.
 */
class OpcUaRuntimeEventsIT {

    @Test
    void serverLifecycleAndApplyFailureSurfaceAsRuntimeEvents() throws Exception {
        OpcUaProtocolService service = new OpcUaProtocolService();
        WorkerServer server = new WorkerServer(0, service).start();
        ManagedChannel channel =
                ManagedChannelBuilder.forAddress("127.0.0.1", server.port()).usePlaintext().build();
        int opcPort = freePort();
        BlockingQueue<RuntimeEvent> events = new LinkedBlockingQueue<>();
        try {
            ProtocolDataSourceGrpc.ProtocolDataSourceBlockingStub blocking =
                    ProtocolDataSourceGrpc.newBlockingStub(channel);
            blocking.configure(ConfigureRequest.newBuilder()
                    .setListenPort(opcPort)
                    .setSchema(Schema.newBuilder().setVersion(1).addNodes(SchemaNodeMsg.newBuilder()
                            .setNodeId("temp").setPath("Temperature").setName("Temperature")
                            .setKind("VARIABLE").setDataType("FLOAT64")))
                    .build());

            // Subscribe BEFORE start so SOURCE_START (emitted when the server begins
            // listening) is not missed — events are not buffered.
            ProtocolDataSourceGrpc.ProtocolDataSourceStub async = ProtocolDataSourceGrpc.newStub(channel);
            async.runtimeEvents(StreamRequest.getDefaultInstance(), collectInto(events));
            awaitUntil(() -> service.openRuntimeEventStreams() > 0);

            blocking.start(StartRequest.getDefaultInstance());

            RuntimeEvent started = events.poll(10, TimeUnit.SECONDS);
            assertThat(started).isNotNull();
            assertThat(started.getType()).isEqualTo("SOURCE_START");
            assertThat(started.getAtMicros()).isPositive();

            // A value too short to decode as FLOAT64 fails projection -> ERROR.
            StreamObserver<Ack> ackObserver = noopAckObserver();
            StreamObserver<ValueBatch> values = async.applyValues(ackObserver);
            values.onNext(ValueBatch.newBuilder()
                    .addValues(Value.newBuilder()
                            .setNodeId("temp")
                            .setValueEnc(ByteString.copyFrom(new byte[] {1, 2})))
                    .build());
            values.onCompleted();

            RuntimeEvent error = events.poll(10, TimeUnit.SECONDS);
            assertThat(error).isNotNull();
            assertThat(error.getType()).isEqualTo("ERROR");
            assertThat(error.getDetail()).contains("temp");

            blocking.stop(StopRequest.getDefaultInstance());

            RuntimeEvent stopped = events.poll(10, TimeUnit.SECONDS);
            assertThat(stopped).isNotNull();
            assertThat(stopped.getType()).isEqualTo("SOURCE_STOP");
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            server.stop();
        }
    }

    private static StreamObserver<RuntimeEvent> collectInto(BlockingQueue<RuntimeEvent> events) {
        return new StreamObserver<>() {
            @Override
            public void onNext(RuntimeEvent event) {
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

    private static StreamObserver<Ack> noopAckObserver() {
        return new StreamObserver<>() {
            @Override
            public void onNext(Ack ack) {
            }

            @Override
            public void onError(Throwable t) {
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
