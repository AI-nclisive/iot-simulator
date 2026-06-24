package com.epam.iotsim.worker.opcua;

import com.epam.iotsim.protocolmodel.ValueCodec;
import com.epam.iotsim.workercontract.WorkerContract;
import com.epam.iotsim.workercontract.v1.Ack;
import com.epam.iotsim.workercontract.v1.ConfigureRequest;
import com.epam.iotsim.workercontract.v1.HealthRequest;
import com.epam.iotsim.workercontract.v1.HealthResponse;
import com.epam.iotsim.workercontract.v1.HelloRequest;
import com.epam.iotsim.workercontract.v1.HelloResponse;
import com.epam.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.epam.iotsim.workercontract.v1.SchemaNodeMsg;
import com.epam.iotsim.workercontract.v1.StartRequest;
import com.epam.iotsim.workercontract.v1.StopRequest;
import com.epam.iotsim.workercontract.v1.Value;
import com.epam.iotsim.workercontract.v1.ValueBatch;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implements the {@code ProtocolDataSource} contract backed by a real Milo OPC UA
 * server: Configure builds the address space from the schema, Start/Stop run the
 * server, ApplyValues projects neutral values onto OPC UA variables.
 * See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
public class OpcUaProtocolService extends ProtocolDataSourceGrpc.ProtocolDataSourceImplBase {

    private final AtomicReference<String> state = new AtomicReference<>("READY");
    private final AtomicLong applied = new AtomicLong();
    private final AtomicInteger configuredNodes = new AtomicInteger();
    private final AtomicReference<OpcUaServerRuntime> serverRuntime = new AtomicReference<>();
    private final Map<String, String> nodeDataTypes = new ConcurrentHashMap<>();

    /** Total values received via ApplyValues (introspection/tests). */
    public long appliedCount() {
        return applied.get();
    }

    /** Number of schema nodes received via Configure (introspection/tests). */
    public int configuredNodeCount() {
        return configuredNodes.get();
    }

    @Override
    public void hello(HelloRequest request, StreamObserver<HelloResponse> obs) {
        obs.onNext(HelloResponse.newBuilder()
                .setContractVersion(WorkerContract.VERSION)
                .setProtocol("OPC_UA")
                .addAllCapabilities(List.of("BOOL", "INT32", "FLOAT64", "STRING"))
                .build());
        obs.onCompleted();
    }

    @Override
    public void configure(ConfigureRequest request, StreamObserver<Ack> obs) {
        List<VarDef> variables = new ArrayList<>();
        nodeDataTypes.clear();
        for (SchemaNodeMsg node : request.getSchema().getNodesList()) {
            if ("VARIABLE".equals(node.getKind())) {
                variables.add(new VarDef(node.getNodeId(), node.getName(), node.getDataType()));
                nodeDataTypes.put(node.getNodeId(), node.getDataType());
            }
        }
        serverRuntime.set(new OpcUaServerRuntime(request.getListenPort(), variables));
        configuredNodes.set(request.getSchema().getNodesCount());
        state.set("CONFIGURED");
        ackOk(obs, "configured " + variables.size() + " variables");
    }

    @Override
    public void start(StartRequest request, StreamObserver<Ack> obs) {
        OpcUaServerRuntime runtime = serverRuntime.get();
        if (runtime != null) {
            runtime.start();
        }
        state.set("RUNNING");
        ackOk(obs, "started");
    }

    @Override
    public void stop(StopRequest request, StreamObserver<Ack> obs) {
        OpcUaServerRuntime runtime = serverRuntime.get();
        if (runtime != null) {
            runtime.stop();
        }
        state.set("STOPPED");
        ackOk(obs, "stopped");
    }

    @Override
    public StreamObserver<ValueBatch> applyValues(StreamObserver<Ack> responseObserver) {
        return new StreamObserver<>() {
            private long received;

            @Override
            public void onNext(ValueBatch batch) {
                received += batch.getValuesCount();
                applied.addAndGet(batch.getValuesCount());
                project(batch);
            }

            @Override
            public void onError(Throwable t) {
            }

            @Override
            public void onCompleted() {
                responseObserver.onNext(Ack.newBuilder().setOk(true)
                        .setMessage("applied " + received).build());
                responseObserver.onCompleted();
            }
        };
    }

    private void project(ValueBatch batch) {
        OpcUaServerRuntime runtime = serverRuntime.get();
        if (runtime == null) {
            return;
        }
        for (Value value : batch.getValuesList()) {
            String dataType = nodeDataTypes.get(value.getNodeId());
            if (dataType != null) {
                Object decoded = ValueCodec.decode(
                        OpcUaTypes.codecKind(dataType), value.getValueEnc().toByteArray());
                runtime.updateValue(value.getNodeId(), OpcUaTypes.toOpcUaValue(dataType, decoded));
            }
        }
    }

    @Override
    public void health(HealthRequest request, StreamObserver<HealthResponse> obs) {
        String current = state.get();
        obs.onNext(HealthResponse.newBuilder()
                .setLive(true)
                .setReady(!"READY".equals(current))
                .setState(current)
                .build());
        obs.onCompleted();
    }

    private static void ackOk(StreamObserver<Ack> obs, String message) {
        obs.onNext(Ack.newBuilder().setOk(true).setMessage(message).build());
        obs.onCompleted();
    }
}
