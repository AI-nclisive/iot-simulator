package com.ainclusive.iotsim.supervisor;

import com.ainclusive.iotsim.workercontract.WorkerContract;
import com.ainclusive.iotsim.workercontract.v1.Ack;
import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.HealthRequest;
import com.ainclusive.iotsim.workercontract.v1.HealthResponse;
import com.ainclusive.iotsim.workercontract.v1.HelloRequest;
import com.ainclusive.iotsim.workercontract.v1.HelloResponse;
import com.ainclusive.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.ainclusive.iotsim.workercontract.v1.Schema;
import com.ainclusive.iotsim.workercontract.v1.StartRequest;
import com.ainclusive.iotsim.workercontract.v1.StopRequest;
import com.ainclusive.iotsim.workercontract.v1.Value;
import com.ainclusive.iotsim.workercontract.v1.ValueBatch;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Supervisor-side IPC client to one worker over loopback gRPC. The {@code hello}
 * handshake refuses a mismatched contract major version.
 * See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
public final class WorkerClient implements AutoCloseable {

    private final ManagedChannel channel;
    private final ProtocolDataSourceGrpc.ProtocolDataSourceBlockingStub stub;

    public WorkerClient(String host, int port) {
        this.channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
        this.stub = ProtocolDataSourceGrpc.newBlockingStub(channel);
    }

    public HelloResponse hello() {
        HelloResponse response = stub.hello(
                HelloRequest.newBuilder().setContractVersion(WorkerContract.VERSION).build());
        if (WorkerContract.major(response.getContractVersion()) != WorkerContract.major(WorkerContract.VERSION)) {
            throw new WorkerContractMismatchException(WorkerContract.VERSION, response.getContractVersion());
        }
        return response;
    }

    public Ack configure(Schema schema, int listenPort) {
        return stub.configure(ConfigureRequest.newBuilder()
                .setSchema(schema)
                .setListenPort(listenPort)
                .build());
    }

    public Ack start() {
        return stub.start(StartRequest.getDefaultInstance());
    }

    public Ack stop() {
        return stub.stop(StopRequest.getDefaultInstance());
    }

    public HealthResponse health() {
        return stub.health(HealthRequest.getDefaultInstance());
    }

    /** Streams values to the worker (client-streaming) and waits for the ack. */
    public int applyValues(List<Value> values) {
        ProtocolDataSourceGrpc.ProtocolDataSourceStub async = ProtocolDataSourceGrpc.newStub(channel);
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        StreamObserver<ValueBatch> requestStream = async.applyValues(new StreamObserver<>() {
            @Override
            public void onNext(Ack ack) {
                // ack captured on completion; nothing to do per-message
            }

            @Override
            public void onError(Throwable t) {
                error.set(t);
                done.countDown();
            }

            @Override
            public void onCompleted() {
                done.countDown();
            }
        });
        try {
            requestStream.onNext(ValueBatch.newBuilder().addAllValues(values).build());
            requestStream.onCompleted();
            if (!done.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("applyValues timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted during applyValues", e);
        }
        if (error.get() != null) {
            throw new IllegalStateException("applyValues failed", error.get());
        }
        return values.size();
    }

    @Override
    public void close() {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            channel.shutdownNow();
        }
    }
}
