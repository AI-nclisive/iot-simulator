package com.ainclusive.iotsim.worker.modbus;

import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import com.ainclusive.iotsim.workercontract.WorkerContract;
import com.ainclusive.iotsim.workercontract.v1.Ack;
import com.ainclusive.iotsim.workercontract.v1.CaptureRequest;
import com.ainclusive.iotsim.workercontract.v1.ClientEvent;
import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.HealthRequest;
import com.ainclusive.iotsim.workercontract.v1.HealthResponse;
import com.ainclusive.iotsim.workercontract.v1.HelloRequest;
import com.ainclusive.iotsim.workercontract.v1.HelloResponse;
import com.ainclusive.iotsim.workercontract.v1.InjectFaultRequest;
import com.ainclusive.iotsim.workercontract.v1.NodeBatch;
import com.ainclusive.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.ainclusive.iotsim.workercontract.v1.RuntimeEvent;
import com.ainclusive.iotsim.workercontract.v1.ScanEvent;
import com.ainclusive.iotsim.workercontract.v1.ScanProgress;
import com.ainclusive.iotsim.workercontract.v1.ScanRequest;
import com.ainclusive.iotsim.workercontract.v1.ScanResponse;
import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import com.ainclusive.iotsim.workercontract.v1.ShutdownRequest;
import com.ainclusive.iotsim.workercontract.v1.StartRequest;
import com.ainclusive.iotsim.workercontract.v1.StopRequest;
import com.ainclusive.iotsim.workercontract.v1.StreamRequest;
import com.ainclusive.iotsim.workercontract.v1.TestConnectionRequest;
import com.ainclusive.iotsim.workercontract.v1.TestConnectionResponse;
import com.ainclusive.iotsim.workercontract.v1.Value;
import com.ainclusive.iotsim.workercontract.v1.ValueBatch;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Implements the {@code ProtocolDataSource} contract backed by a real j2mod
 * Modbus TCP slave: Configure builds the register/coil layout from the
 * schema (default contiguous rule, protocol-model §5), Start/Stop run the
 * slave, ApplyValues projects neutral values onto registers/coils. Scan and
 * Capture act as a Modbus master against a real endpoint — see {@link
 * ModbusDiscovery} and {@link ModbusCapture}. Mirrors {@code worker-opcua}'s
 * {@code OpcUaProtocolService}. See openspec/specs/worker-contract/spec.md.
 */
public class ModbusProtocolService extends ProtocolDataSourceGrpc.ProtocolDataSourceImplBase {

    private static final int SHUTDOWN_FLUSH_DELAY_MS = 200;
    private static final int SCAN_NODE_BATCH_SIZE = 500;

    private final AtomicReference<String> state = new AtomicReference<>("READY");
    private final AtomicLong applied = new AtomicLong();
    private final AtomicInteger configuredNodes = new AtomicInteger();
    private final AtomicReference<ModbusServerRuntime> serverRuntime = new AtomicReference<>();
    private final Map<String, String> nodeDataTypes = new ConcurrentHashMap<>();
    /** Nodes whose declared data type cannot be materialized over Modbus (protocol-model §2 "unknown"). */
    private final Set<String> unsupportedNodes = ConcurrentHashMap.newKeySet();
    private final ClientEventHub clientEventHub = new ClientEventHub();
    private final RuntimeEventHub runtimeEventHub = new RuntimeEventHub();

    /** Active faults keyed by {@code "kind:layer"}, mirroring {@code OpcUaProtocolService}. */
    record FaultState(boolean active, long delayMs) {
        static final long DEFAULT_DELAY_MS = 100L;
    }

    private final Map<String, FaultState> activeFaults = new ConcurrentHashMap<>();

    /** Total values received via ApplyValues (introspection/tests). */
    public long appliedCount() {
        return applied.get();
    }

    /** Number of schema nodes received via Configure (introspection/tests). */
    public int configuredNodeCount() {
        return configuredNodes.get();
    }

    /** Number of open supervisor {@code ClientEvents} streams (introspection/tests). */
    public int openClientEventStreams() {
        return clientEventHub.openStreamCount();
    }

    /** Number of open supervisor {@code RuntimeEvents} streams (introspection/tests). */
    public int openRuntimeEventStreams() {
        return runtimeEventHub.openStreamCount();
    }

    /** The address assignment computed for the currently configured schema (introspection/tests). */
    Map<String, ModbusServerRuntime.NodeAssignment> assignments() {
        ModbusServerRuntime runtime = serverRuntime.get();
        return runtime == null ? Map.of() : runtime.assignments();
    }

    @Override
    public void hello(HelloRequest request, StreamObserver<HelloResponse> obs) {
        obs.onNext(HelloResponse.newBuilder()
                .setContractVersion(WorkerContract.VERSION)
                .setProtocol("MODBUS_TCP")
                .addAllCapabilities(List.of("BOOL", "INT16", "UINT16", "INT32", "UINT32", "FLOAT32"))
                .build());
        obs.onCompleted();
    }

    @Override
    public void configure(ConfigureRequest request, StreamObserver<Ack> obs) {
        nodeDataTypes.clear();
        unsupportedNodes.clear();
        List<ModbusServerRuntime.VarSpec> vars = new ArrayList<>();
        for (SchemaNodeMsg node : request.getSchema().getNodesList()) {
            if (!"VARIABLE".equals(node.getKind())) {
                continue;
            }
            String explicitKind = node.getModbusRegisterKind().isBlank() ? null : node.getModbusRegisterKind();
            Integer explicitAddress = explicitKind == null ? null : node.getModbusAddress();
            vars.add(new ModbusServerRuntime.VarSpec(node.getNodeId(), node.getDataType(), node.getAccess(),
                    explicitKind, explicitAddress));
            if (ModbusTypes.isSupported(node.getDataType())) {
                nodeDataTypes.put(node.getNodeId(), node.getDataType());
            }
        }
        unsupportedNodes.addAll(ModbusServerRuntime.unsupportedNodes(vars));
        String bindAddress = request.getOptions().getOrDefault("bindAddress", "127.0.0.1");
        int unitId = parseUnitId(request.getOptions().getOrDefault("unitId", String.valueOf(ModbusDiscovery.DEFAULT_UNIT_ID)));
        try {
            serverRuntime.set(new ModbusServerRuntime(vars, request.getListenPort(),
                    InetAddress.getByName(bindAddress), unitId, runtimeEventHub::emit));
        } catch (Exception e) {
            obs.onNext(Ack.newBuilder().setOk(false).setMessage(e.getMessage()).build());
            obs.onCompleted();
            return;
        }
        configuredNodes.set(request.getSchema().getNodesCount());
        state.set("CONFIGURED");
        ackOk(obs, "configured " + vars.size() + " variables");
    }

    private static int parseUnitId(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return ModbusDiscovery.DEFAULT_UNIT_ID;
        }
    }

    @Override
    public void start(StartRequest request, StreamObserver<Ack> obs) {
        ModbusServerRuntime runtime = serverRuntime.get();
        if (runtime != null) {
            try {
                runtime.start();
            } catch (ModbusServerRuntime.ModbusStartException e) {
                state.set("ERROR");
                obs.onNext(Ack.newBuilder().setOk(false).setMessage(e.getMessage()).build());
                obs.onCompleted();
                return;
            }
        }
        state.set("RUNNING");
        ackOk(obs, "started");
    }

    @Override
    public void stop(StopRequest request, StreamObserver<Ack> obs) {
        ModbusServerRuntime runtime = serverRuntime.get();
        if (runtime != null) {
            runtime.stop();
        }
        state.set("STOPPED");
        ackOk(obs, "stopped");
    }

    @Override
    public void shutdown(ShutdownRequest request, StreamObserver<Ack> obs) {
        ModbusServerRuntime runtime = serverRuntime.get();
        if (runtime != null) {
            runtime.stop();
        }
        state.set("STOPPED");
        ackOk(obs, "shutting down");
        Thread exit = new Thread(() -> {
            try {
                Thread.sleep(SHUTDOWN_FLUSH_DELAY_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.exit(0);
        }, "worker-shutdown-exit");
        exit.setDaemon(true);
        exit.start();
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

    @Override
    public void testConnection(TestConnectionRequest request, StreamObserver<TestConnectionResponse> obs) {
        ModbusDiscovery.ConnectionTest result = ModbusDiscovery.testConnection(
                request.getEndpointUrl(), request.hasUnitId() ? (int) request.getUnitId() : ModbusDiscovery.DEFAULT_UNIT_ID);
        obs.onNext(TestConnectionResponse.newBuilder()
                .setStatus(result.status())
                .setMessage(orEmpty(result.message()))
                .build());
        obs.onCompleted();
    }

    @Override
    public void scan(ScanRequest request, StreamObserver<ScanEvent> responseObserver) {
        ServerCallStreamObserver<ScanEvent> obs = (ServerCallStreamObserver<ScanEvent>) responseObserver;
        sendIfNotCancelled(obs, ScanEvent.newBuilder()
                .setProgress(ScanProgress.newBuilder().setPhase("CONNECTING"))
                .build());
        ModbusDiscovery.ScanOutcome outcome = ModbusDiscovery.scan(
                request.getEndpointUrl(), request.hasUnitId() ? (int) request.getUnitId() : ModbusDiscovery.DEFAULT_UNIT_ID,
                request.getMaxNodes(),
                () -> sendIfNotCancelled(obs, ScanEvent.newBuilder()
                        .setProgress(ScanProgress.newBuilder().setPhase("CONNECTED"))
                        .build()),
                soFar -> sendIfNotCancelled(obs, ScanEvent.newBuilder()
                        .setProgress(ScanProgress.newBuilder().setPhase("SCANNING").setDiscoveredSoFar(soFar))
                        .build()));
        if (obs.isCancelled()) {
            return;
        }
        List<SchemaNodeMsg> nodes = outcome.nodes();
        for (int start = 0; start < nodes.size() && !obs.isCancelled(); start += SCAN_NODE_BATCH_SIZE) {
            int end = Math.min(start + SCAN_NODE_BATCH_SIZE, nodes.size());
            sendIfNotCancelled(obs, ScanEvent.newBuilder()
                    .setNodeBatch(NodeBatch.newBuilder().addAllNodes(nodes.subList(start, end)))
                    .build());
        }
        if (obs.isCancelled()) {
            return;
        }
        sendIfNotCancelled(obs, ScanEvent.newBuilder()
                .setResult(ScanResponse.newBuilder()
                        .setStatus(outcome.status())
                        .setTruncated(outcome.truncated())
                        .setDiscoveredCount(nodes.size())
                        .setUnknownCount(outcome.unknownCount())
                        .setMessage(orEmpty(outcome.message())))
                .build());
        obs.onCompleted();
    }

    private static void sendIfNotCancelled(ServerCallStreamObserver<ScanEvent> obs, ScanEvent event) {
        if (!obs.isCancelled()) {
            obs.onNext(event);
        }
    }

    @Override
    public void capture(CaptureRequest request, StreamObserver<ValueBatch> responseObserver) {
        List<ModbusCapture.NodeSpec> nodes = new ArrayList<>();
        for (SchemaNodeMsg node : request.getSchema().getNodesList()) {
            if ("VARIABLE".equals(node.getKind()) && ModbusTypes.isSupported(node.getDataType())) {
                nodes.add(new ModbusCapture.NodeSpec(node.getNodeId(), node.getDataType()));
            }
        }
        ServerCallStreamObserver<ValueBatch> serverObserver = (ServerCallStreamObserver<ValueBatch>) responseObserver;
        AtomicReference<ModbusCapture> capture = new AtomicReference<>();
        serverObserver.setOnCancelHandler(() -> {
            ModbusCapture c = capture.get();
            if (c != null) {
                c.stop();
            }
        });
        try {
            capture.set(ModbusCapture.start(request.getEndpointUrl(),
                    request.hasUnitId() ? (int) request.getUnitId() : ModbusDiscovery.DEFAULT_UNIT_ID, nodes,
                    batch -> {
                        synchronized (serverObserver) {
                            if (!serverObserver.isCancelled()) {
                                responseObserver.onNext(ValueBatch.newBuilder().addAllValues(batch).build());
                            }
                        }
                    }));
            if (serverObserver.isCancelled()) {
                capture.get().stop();
            }
        } catch (Exception e) {
            responseObserver.onError(io.grpc.Status.UNAVAILABLE
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    @Override
    public void clientEvents(StreamRequest request, StreamObserver<ClientEvent> responseObserver) {
        clientEventHub.register((ServerCallStreamObserver<ClientEvent>) responseObserver);
    }

    @Override
    public void runtimeEvents(StreamRequest request, StreamObserver<RuntimeEvent> responseObserver) {
        runtimeEventHub.register((ServerCallStreamObserver<RuntimeEvent>) responseObserver);
    }

    @Override
    public void injectFault(InjectFaultRequest request, StreamObserver<Ack> responseObserver) {
        String key = request.getKind() + ":" + request.getLayer();
        if (request.getActive()) {
            long delayMs = FaultState.DEFAULT_DELAY_MS;
            String delayParam = request.getParamsMap().get("delay_ms");
            if (delayParam != null && !delayParam.isBlank()) {
                try {
                    delayMs = Long.parseLong(delayParam);
                } catch (NumberFormatException ignored) {
                    // fall back to default
                }
            }
            activeFaults.put(key, new FaultState(true, delayMs));
        } else {
            activeFaults.remove(key);
        }
        responseObserver.onNext(Ack.newBuilder().setOk(true).build());
        responseObserver.onCompleted();
    }

    /** Returns {@code true} when a fault of the given kind is active on ANY layer. */
    public boolean isFaultActive(String kind) {
        return activeFaults.keySet().stream().anyMatch(k -> k.startsWith(kind + ":"));
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
        ModbusServerRuntime runtime = serverRuntime.get();
        if (runtime == null) {
            return;
        }
        if (isFaultActive("CONNECTION_DROP")) {
            runtimeEventHub.emit(RuntimeEvent.newBuilder()
                    .setType("ERROR")
                    .setAtMicros(System.currentTimeMillis() * 1_000L)
                    .setDetail("CONNECTION_DROP fault active — value batch dropped")
                    .build());
            return;
        }
        if (isFaultActive("DELAY")) {
            long delayMs = activeFaults.entrySet().stream()
                    .filter(e -> e.getKey().startsWith("DELAY:"))
                    .map(e -> e.getValue().delayMs())
                    .findFirst()
                    .orElse(FaultState.DEFAULT_DELAY_MS);
            try {
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        for (Value value : batch.getValuesList()) {
            // BAD_VALUE / MISSING_VALUE have no Modbus quality/status channel to carry a "bad
            // read" signal (unlike OPC UA's StatusCode) — leaving the register unchanged is the
            // closest available proxy: a Modbus client reading it sees the last good value.
            if (isFaultActive("BAD_VALUE") || isFaultActive("MISSING_VALUE")) {
                continue;
            }
            String dataType = nodeDataTypes.get(value.getNodeId());
            if (dataType == null) {
                if (unsupportedNodes.contains(value.getNodeId())) {
                    runtimeEventHub.emit(RuntimeEvent.newBuilder()
                            .setType("ERROR")
                            .setAtMicros(System.currentTimeMillis() * 1_000L)
                            .setDetail("cannot apply value for unsupported Modbus data type on node "
                                    + value.getNodeId())
                            .build());
                }
                continue;
            }
            try {
                Object decoded = ValueCodec.decode(ModbusTypes.codecKind(dataType), value.getValueEnc().toByteArray());
                runtime.updateValue(value.getNodeId(), decoded);
            } catch (RuntimeException e) {
                runtimeEventHub.emit(RuntimeEvent.newBuilder()
                        .setType("ERROR")
                        .setAtMicros(System.currentTimeMillis() * 1_000L)
                        .setDetail("failed to apply value for node " + value.getNodeId() + ": " + e.getMessage())
                        .build());
            }
        }
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void ackOk(StreamObserver<Ack> obs, String message) {
        obs.onNext(Ack.newBuilder().setOk(true).setMessage(message).build());
        obs.onCompleted();
    }
}
