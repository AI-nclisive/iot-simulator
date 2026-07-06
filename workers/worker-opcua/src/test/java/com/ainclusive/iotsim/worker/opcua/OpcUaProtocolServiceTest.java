package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.workercontract.v1.Ack;
import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.InjectFaultRequest;
import com.ainclusive.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.ainclusive.iotsim.workercontract.v1.RuntimeEvent;
import com.ainclusive.iotsim.workercontract.v1.StartRequest;
import com.ainclusive.iotsim.workercontract.v1.StreamRequest;
import com.ainclusive.iotsim.workercontract.v1.Value;
import com.ainclusive.iotsim.workercontract.v1.ValueBatch;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Unit-level tests for {@link OpcUaProtocolService} fault-injection behaviour (IS-088).
 * Drives the service over a real loopback gRPC connection so the full RPC path is covered.
 */
class OpcUaProtocolServiceTest {

    private static OpcUaProtocolService service;
    private static WorkerServer server;
    private static ManagedChannel channel;
    private static ProtocolDataSourceGrpc.ProtocolDataSourceBlockingStub stub;
    private static ProtocolDataSourceGrpc.ProtocolDataSourceStub asyncStub;

    @BeforeAll
    static void startServer() throws Exception {
        service = new OpcUaProtocolService();
        server = new WorkerServer(0, service).start();
        channel = ManagedChannelBuilder.forAddress("127.0.0.1", server.port())
                .usePlaintext()
                .build();
        stub = ProtocolDataSourceGrpc.newBlockingStub(channel);
        asyncStub = ProtocolDataSourceGrpc.newStub(channel);
        // Configure and start with an ephemeral port so there are no port conflicts.
        stub.configure(ConfigureRequest.newBuilder().setListenPort(0).build());
        stub.start(StartRequest.getDefaultInstance());
    }

    @AfterAll
    static void stopServer() throws Exception {
        channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
        server.stop();
    }

    // -----------------------------------------------------------------------
    // injectFault RPC — blocking fix: response must have ok=true
    // -----------------------------------------------------------------------

    @Test
    void injectFaultReturnsOkTrue() {
        Ack ack = stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("BAD_VALUE")
                .setLayer("NEUTRAL")
                .setActive(true)
                .build());
        assertThat(ack.getOk()).isTrue();

        // clean up so this fault does not bleed into other tests
        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("BAD_VALUE")
                .setLayer("NEUTRAL")
                .setActive(false)
                .build());
    }

    // -----------------------------------------------------------------------
    // Fault state management
    // -----------------------------------------------------------------------

    @Test
    void clearingFaultRemovesItAndIsFaultActiveReturnsFalse() {
        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("CONNECTION_DROP")
                .setLayer("NEUTRAL")
                .setActive(true)
                .build());
        assertThat(service.isFaultActive("CONNECTION_DROP")).isTrue();

        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("CONNECTION_DROP")
                .setLayer("NEUTRAL")
                .setActive(false)
                .build());
        assertThat(service.isFaultActive("CONNECTION_DROP")).isFalse();
    }

    @Test
    void sameKindOnDifferentLayersAreIndependent() {
        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("DELAY")
                .setLayer("NEUTRAL")
                .setActive(true)
                .build());
        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("DELAY")
                .setLayer("PROTOCOL")
                .setActive(true)
                .build());

        // both layers active → isFaultActive returns true
        assertThat(service.isFaultActive("DELAY")).isTrue();

        // clear NEUTRAL only — PROTOCOL still active
        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("DELAY")
                .setLayer("NEUTRAL")
                .setActive(false)
                .build());
        assertThat(service.isFaultActive("DELAY")).isTrue();
        assertThat(service.faultState("DELAY", "NEUTRAL")).isNull();
        assertThat(service.faultState("DELAY", "PROTOCOL")).isNotNull();

        // clear PROTOCOL too
        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("DELAY")
                .setLayer("PROTOCOL")
                .setActive(false)
                .build());
        assertThat(service.isFaultActive("DELAY")).isFalse();
    }

    @Test
    void delayMsParamIsStoredInFaultState() {
        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("DELAY")
                .setLayer("NEUTRAL")
                .setActive(true)
                .putParams("delay_ms", "250")
                .build());

        OpcUaProtocolService.FaultState state = service.faultState("DELAY", "NEUTRAL");
        assertThat(state).isNotNull();
        assertThat(state.delayMs()).isEqualTo(250L);

        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("DELAY")
                .setLayer("NEUTRAL")
                .setActive(false)
                .build());
    }

    @Test
    void delayFaultWithoutParamDefaultsTo100Ms() {
        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("DELAY")
                .setLayer("NEUTRAL")
                .setActive(true)
                .build());

        OpcUaProtocolService.FaultState state = service.faultState("DELAY", "NEUTRAL");
        assertThat(state).isNotNull();
        assertThat(state.delayMs()).isEqualTo(OpcUaProtocolService.FaultState.DEFAULT_DELAY_MS);

        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("DELAY")
                .setLayer("NEUTRAL")
                .setActive(false)
                .build());
    }

    // -----------------------------------------------------------------------
    // Projection behaviour under active faults
    // -----------------------------------------------------------------------

    @Test
    void delayFaultIsActiveAfterInject() {
        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("DELAY")
                .setLayer("NEUTRAL")
                .setActive(true)
                .build());

        // The simplest verifiable assertion: the fault is tracked as active.
        // The actual Thread.sleep fires during applyValues projection, which is an
        // integration concern covered by the end-to-end tests; here we confirm the
        // state machine is correct.
        assertThat(service.isFaultActive("DELAY")).isTrue();

        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("DELAY")
                .setLayer("NEUTRAL")
                .setActive(false)
                .build());
        assertThat(service.isFaultActive("DELAY")).isFalse();
    }

    @Test
    void connectionDropFaultEmitsRuntimeErrorOnValueBatch() throws Exception {
        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("CONNECTION_DROP")
                .setLayer("NEUTRAL")
                .setActive(true)
                .build());

        // Subscribe to runtime events *before* sending the value batch so the
        // ERROR event is observed on the stream.
        List<RuntimeEvent> events = new ArrayList<>();
        CountDownLatch errorSeen = new CountDownLatch(1);
        asyncStub.runtimeEvents(StreamRequest.getDefaultInstance(), new StreamObserver<>() {
            @Override
            public void onNext(RuntimeEvent event) {
                events.add(event);
                if ("ERROR".equals(event.getType())) {
                    errorSeen.countDown();
                }
            }

            @Override
            public void onError(Throwable t) {
                errorSeen.countDown();
            }

            @Override
            public void onCompleted() {}
        });

        // Wait for the subscription to be fully registered on the server side before
        // sending the value batch — RuntimeEventHub drops events when no stream is open.
        long deadline = System.currentTimeMillis() + 2_000;
        while (service.openRuntimeEventStreams() == 0 && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        assertThat(service.openRuntimeEventStreams()).isGreaterThan(0);

        // Send a value batch — project() should detect CONNECTION_DROP and emit an ERROR.
        CountDownLatch applyDone = new CountDownLatch(1);
        StreamObserver<ValueBatch> applyStream = asyncStub.applyValues(new StreamObserver<>() {
            @Override
            public void onNext(Ack ack) {}

            @Override
            public void onError(Throwable t) {
                applyDone.countDown();
            }

            @Override
            public void onCompleted() {
                applyDone.countDown();
            }
        });
        applyStream.onNext(ValueBatch.newBuilder()
                .addValues(Value.newBuilder().setNodeId("x").build())
                .build());
        applyStream.onCompleted();
        assertThat(applyDone.await(5, TimeUnit.SECONDS)).isTrue();

        // The ERROR runtime event should arrive within 2 s.
        assertThat(errorSeen.await(2, TimeUnit.SECONDS)).isTrue();
        assertThat(events).anySatisfy(e -> {
            assertThat(e.getType()).isEqualTo("ERROR");
            assertThat(e.getDetail()).contains("CONNECTION_DROP");
        });

        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("CONNECTION_DROP")
                .setLayer("NEUTRAL")
                .setActive(false)
                .build());
    }

    @Test
    void badValueFaultIsFaultActiveReturnsTrueAndCounterStillIncrements() throws Exception {
        long beforeCount = service.appliedCount();

        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("BAD_VALUE")
                .setLayer("NEUTRAL")
                .setActive(true)
                .build());
        assertThat(service.isFaultActive("BAD_VALUE")).isTrue();

        // Send a batch — values are skipped in the for-loop (not projected to OPC UA
        // variables), but the applied counter still increments because it counts
        // ingested values, not projected ones.
        CountDownLatch done = new CountDownLatch(1);
        StreamObserver<ValueBatch> applyStream = asyncStub.applyValues(new StreamObserver<>() {
            @Override
            public void onNext(Ack ack) {}

            @Override
            public void onError(Throwable t) {
                done.countDown();
            }

            @Override
            public void onCompleted() {
                done.countDown();
            }
        });
        applyStream.onNext(ValueBatch.newBuilder()
                .addValues(Value.newBuilder().setNodeId("a").build())
                .addValues(Value.newBuilder().setNodeId("b").build())
                .build());
        applyStream.onCompleted();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();

        // The applied counter reflects the two ingested values.
        assertThat(service.appliedCount()).isGreaterThanOrEqualTo(beforeCount + 2);

        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("BAD_VALUE")
                .setLayer("NEUTRAL")
                .setActive(false)
                .build());
        assertThat(service.isFaultActive("BAD_VALUE")).isFalse();
    }

    @Test
    void missingValueFaultIsFaultActiveReturnsTrue() {
        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("MISSING_VALUE")
                .setLayer("NEUTRAL")
                .setActive(true)
                .build());
        assertThat(service.isFaultActive("MISSING_VALUE")).isTrue();

        stub.injectFault(InjectFaultRequest.newBuilder()
                .setKind("MISSING_VALUE")
                .setLayer("NEUTRAL")
                .setActive(false)
                .build());
        assertThat(service.isFaultActive("MISSING_VALUE")).isFalse();
    }
}
