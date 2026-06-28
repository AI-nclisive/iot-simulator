package com.ainclusive.iotsim.supervisor;

import com.ainclusive.iotsim.workercontract.WorkerContract;
import com.ainclusive.iotsim.workercontract.v1.Ack;
import com.ainclusive.iotsim.workercontract.v1.CaptureRequest;
import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.HealthRequest;
import com.ainclusive.iotsim.workercontract.v1.HealthResponse;
import com.ainclusive.iotsim.workercontract.v1.HelloRequest;
import com.ainclusive.iotsim.workercontract.v1.HelloResponse;
import com.ainclusive.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.ainclusive.iotsim.workercontract.v1.ScanRequest;
import com.ainclusive.iotsim.workercontract.v1.ScanResponse;
import com.ainclusive.iotsim.workercontract.v1.Schema;
import com.ainclusive.iotsim.workercontract.v1.StartRequest;
import com.ainclusive.iotsim.workercontract.v1.StopRequest;
import com.ainclusive.iotsim.workercontract.v1.TestConnectionRequest;
import com.ainclusive.iotsim.workercontract.v1.TestConnectionResponse;
import com.ainclusive.iotsim.workercontract.v1.Value;
import com.ainclusive.iotsim.workercontract.v1.ValueBatch;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.ClientResponseObserver;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Supervisor-side IPC client to one worker over loopback gRPC. The {@code hello}
 * handshake refuses a mismatched contract major version.
 * See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
public final class WorkerClient implements AutoCloseable {

    // Scan/test-connection reach an external endpoint; the worker bounds its own
    // attempt, these deadlines just stop a hung worker from blocking the caller.
    private static final long TEST_CONNECTION_TIMEOUT_SECONDS = 30;
    private static final long SCAN_TIMEOUT_SECONDS = 120;

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

    /**
     * Probes a real source's reachability/auth via the worker (client mode). The
     * worker bounds its own connect attempt; the deadline guards against a hung
     * worker. See backend-specs/05_API_CONTRACT.md §Scan.
     */
    public TestConnectionResponse testConnection(TestConnectionRequest request) {
        return stub.withDeadlineAfter(TEST_CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .testConnection(request);
    }

    /** Browses a real source via the worker (client mode) into neutral schema nodes. */
    public ScanResponse scan(ScanRequest request) {
        return stub.withDeadlineAfter(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS).scan(request);
    }

    /**
     * Probes the worker's health under a deadline so a hung worker cannot block the
     * caller. Returns {@code true} only when the worker answers in time and reports
     * itself live; any RPC error, timeout, or {@code live=false} returns
     * {@code false}. Used by the supervisor's health-monitoring loop.
     */
    public boolean isHealthy(Duration timeout) {
        try {
            HealthResponse response = stub
                    .withDeadlineAfter(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .health(HealthRequest.getDefaultInstance());
            return response.getLive();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * Opens a live-capture stream: the worker subscribes to the real source and
     * streams observed value batches back until the returned handle is cancelled
     * (server-streaming, IS-045). {@code onBatch} is called per published batch;
     * {@code onError} on a non-cancel stream failure. The handle's
     * {@link CaptureHandle#cancel() cancel} ends the stream, which fires the
     * worker's cancel handler so it stops the subscription.
     */
    public CaptureHandle capture(CaptureRequest request, Consumer<ValueBatch> onBatch,
            Consumer<Throwable> onError) {
        ProtocolDataSourceGrpc.ProtocolDataSourceStub async = ProtocolDataSourceGrpc.newStub(channel);
        AtomicReference<ClientCallStreamObserver<CaptureRequest>> requestStream = new AtomicReference<>();
        ClientResponseObserver<CaptureRequest, ValueBatch> observer = new ClientResponseObserver<>() {
            @Override
            public void beforeStart(ClientCallStreamObserver<CaptureRequest> stream) {
                requestStream.set(stream);
            }

            @Override
            public void onNext(ValueBatch batch) {
                onBatch.accept(batch);
            }

            @Override
            public void onError(Throwable t) {
                if (onError != null) {
                    onError.accept(t);
                }
            }

            @Override
            public void onCompleted() {
                // server-streaming ends only via cancel; nothing to do
            }
        };
        async.capture(request, observer);
        return () -> {
            ClientCallStreamObserver<CaptureRequest> stream = requestStream.get();
            if (stream != null) {
                stream.cancel("capture stopped", null);
            }
        };
    }

    /** Cancels an in-progress {@link #capture} stream. */
    @FunctionalInterface
    public interface CaptureHandle {
        void cancel();
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
