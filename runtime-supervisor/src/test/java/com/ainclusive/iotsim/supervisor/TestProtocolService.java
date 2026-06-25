package com.ainclusive.iotsim.supervisor;

import com.ainclusive.iotsim.workercontract.WorkerContract;
import com.ainclusive.iotsim.workercontract.v1.Ack;
import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.HelloRequest;
import com.ainclusive.iotsim.workercontract.v1.HelloResponse;
import com.ainclusive.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.ainclusive.iotsim.workercontract.v1.StartRequest;
import com.ainclusive.iotsim.workercontract.v1.StopRequest;
import com.ainclusive.iotsim.workercontract.v1.ValueBatch;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Minimal in-process worker that satisfies the handshake and counts what it receives. */
final class TestProtocolService extends ProtocolDataSourceGrpc.ProtocolDataSourceImplBase {

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
