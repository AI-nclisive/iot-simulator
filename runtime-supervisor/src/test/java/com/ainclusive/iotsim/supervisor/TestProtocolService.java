package com.ainclusive.iotsim.supervisor;

import com.ainclusive.iotsim.workercontract.WorkerContract;
import com.ainclusive.iotsim.workercontract.v1.Ack;
import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.HealthRequest;
import com.ainclusive.iotsim.workercontract.v1.HealthResponse;
import com.ainclusive.iotsim.workercontract.v1.HelloRequest;
import com.ainclusive.iotsim.workercontract.v1.HelloResponse;
import com.ainclusive.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.ainclusive.iotsim.workercontract.v1.ScanRequest;
import com.ainclusive.iotsim.workercontract.v1.ScanResponse;
import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import com.ainclusive.iotsim.workercontract.v1.StartRequest;
import com.ainclusive.iotsim.workercontract.v1.StopRequest;
import com.ainclusive.iotsim.workercontract.v1.TestConnectionRequest;
import com.ainclusive.iotsim.workercontract.v1.TestConnectionResponse;
import com.ainclusive.iotsim.workercontract.v1.ValueBatch;
import io.grpc.stub.StreamObserver;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/** Minimal in-process worker that satisfies the handshake and counts what it receives. */
final class TestProtocolService extends ProtocolDataSourceGrpc.ProtocolDataSourceImplBase {

    private final AtomicLong applied = new AtomicLong();
    private final AtomicInteger configuredNodes = new AtomicInteger();
    private final AtomicReference<ScanRequest> lastScan = new AtomicReference<>();
    private final AtomicReference<TestConnectionRequest> lastTestConnection = new AtomicReference<>();
    private volatile boolean healthLive = true;
    private volatile boolean healthHang;

    /** The scan request the supervisor sent, for asserting endpoint/credential mapping. */
    ScanRequest lastScanRequest() {
        return lastScan.get();
    }

    TestConnectionRequest lastTestConnectionRequest() {
        return lastTestConnection.get();
    }

    long appliedCount() {
        return applied.get();
    }

    int configuredNodeCount() {
        return configuredNodes.get();
    }

    /** Simulates an alive-but-unhealthy worker: Health answers with {@code live=false}. */
    void setHealthLive(boolean live) {
        this.healthLive = live;
    }

    /** Simulates a hung worker: Health stops answering, so the probe deadline fires. */
    void setHealthHang(boolean hang) {
        this.healthHang = hang;
    }

    @Override
    public void health(HealthRequest request, StreamObserver<HealthResponse> obs) {
        if (healthHang) {
            // Never answer; the supervisor's deadline cancels the probe. Don't complete
            // the observer so this models a worker stuck mid-call.
            return;
        }
        obs.onNext(HealthResponse.newBuilder().setLive(healthLive).setReady(healthLive).build());
        obs.onCompleted();
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
    public void testConnection(TestConnectionRequest request, StreamObserver<TestConnectionResponse> obs) {
        lastTestConnection.set(request);
        obs.onNext(TestConnectionResponse.newBuilder().setStatus("OK").setMessage("ok").build());
        obs.onCompleted();
    }

    @Override
    public void scan(ScanRequest request, StreamObserver<ScanResponse> obs) {
        lastScan.set(request);
        obs.onNext(ScanResponse.newBuilder()
                .setStatus("OK")
                .addNodes(SchemaNodeMsg.newBuilder()
                        .setNodeId("ns=2;s=temp").setPath("Temperature").setName("Temperature")
                        .setKind("VARIABLE").setDataType("FLOAT64").setValueRank("SCALAR").setAccess("READ"))
                .setDiscoveredCount(1)
                .setMessage("discovered 1 nodes")
                .build());
        obs.onCompleted();
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
