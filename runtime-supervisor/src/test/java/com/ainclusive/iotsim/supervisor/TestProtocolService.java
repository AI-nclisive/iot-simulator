package com.ainclusive.iotsim.supervisor;

import com.ainclusive.iotsim.workercontract.WorkerContract;
import com.ainclusive.iotsim.workercontract.v1.Ack;
import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.HealthRequest;
import com.ainclusive.iotsim.workercontract.v1.HealthResponse;
import com.ainclusive.iotsim.workercontract.v1.HelloRequest;
import com.ainclusive.iotsim.workercontract.v1.HelloResponse;
import com.ainclusive.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.ainclusive.iotsim.workercontract.v1.StartRequest;
import com.ainclusive.iotsim.workercontract.v1.StopRequest;
import com.ainclusive.iotsim.workercontract.v1.ValueBatch;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Minimal in-process worker that satisfies the handshake and counts what it receives. */
final class TestProtocolService extends ProtocolDataSourceGrpc.ProtocolDataSourceImplBase {

    private final AtomicLong applied = new AtomicLong();
    private final AtomicInteger configuredNodes = new AtomicInteger();
    private final AtomicInteger healthCallCount = new AtomicInteger();
    /** When false, health() responds with live=false (simulates unresponsive worker). */
    private final AtomicBoolean healthyResponse = new AtomicBoolean(true);

    long appliedCount() {
        return applied.get();
    }

    int configuredNodeCount() {
        return configuredNodes.get();
    }

    int healthCallCount() {
        return healthCallCount.get();
    }

    /** Make subsequent health() calls return live=false. */
    void simulateUnhealthy() {
        healthyResponse.set(false);
    }

    /** Restore healthy responses. */
    void simulateHealthy() {
        healthyResponse.set(true);
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
    public void health(HealthRequest request, StreamObserver<HealthResponse> obs) {
        healthCallCount.incrementAndGet();
        obs.onNext(HealthResponse.newBuilder()
                .setLive(healthyResponse.get())
                .setReady(healthyResponse.get())
                .setState("RUNNING")
                .build());
        obs.onCompleted();
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
