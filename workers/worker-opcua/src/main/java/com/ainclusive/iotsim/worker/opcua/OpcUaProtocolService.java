package com.ainclusive.iotsim.worker.opcua;

import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import com.ainclusive.iotsim.workercontract.WorkerContract;
import com.ainclusive.iotsim.workercontract.v1.Ack;
import com.ainclusive.iotsim.workercontract.v1.CaptureRequest;
import com.ainclusive.iotsim.workercontract.v1.ClientEvent;
import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.ConnectionConfigMsg;
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
import com.ainclusive.iotsim.workercontract.v1.SecurityConfig;
import com.ainclusive.iotsim.workercontract.v1.ShutdownRequest;
import com.ainclusive.iotsim.workercontract.v1.StartRequest;
import com.ainclusive.iotsim.workercontract.v1.StopRequest;
import com.ainclusive.iotsim.workercontract.v1.StreamRequest;
import com.ainclusive.iotsim.workercontract.v1.TestConnectionRequest;
import com.ainclusive.iotsim.workercontract.v1.TestConnectionResponse;
import com.ainclusive.iotsim.workercontract.v1.UserCredential;
import com.ainclusive.iotsim.workercontract.v1.Value;
import com.ainclusive.iotsim.workercontract.v1.ValueBatch;
import io.grpc.Status;
import io.grpc.stub.ServerCallStreamObserver;
import io.grpc.stub.StreamObserver;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;

/**
 * Implements the {@code ProtocolDataSource} contract backed by a real Milo OPC UA
 * server: Configure builds the address space from the schema, Start/Stop run the
 * server, ApplyValues projects neutral values onto OPC UA variables.
 * See openspec/specs/worker-contract/spec.md.
 */
public class OpcUaProtocolService extends ProtocolDataSourceGrpc.ProtocolDataSourceImplBase {

    private static final int SHUTDOWN_FLUSH_DELAY_MS = 200;

    private final AtomicReference<String> state = new AtomicReference<>("READY");
    private final AtomicLong applied = new AtomicLong();
    private final AtomicInteger configuredNodes = new AtomicInteger();
    private final AtomicReference<OpcUaServerRuntime> serverRuntime = new AtomicReference<>();
    private final Map<String, String> nodeDataTypes = new ConcurrentHashMap<>();
    /** Declared array dimensions for variables; absence means scalar. */
    private final Map<String, List<Integer>> nodeArrayDimensions = new ConcurrentHashMap<>();
    /** Default binary encodings for native structures, keyed by variable node id. */
    private final Map<String, NodeId> structureEncodings = new ConcurrentHashMap<>();
    private final Map<String, String> structureDataTypes = new ConcurrentHashMap<>();
    /** Native declarations that can be instantiated from canonical TREE values. */
    private final Map<String, String> treeDataTypes = new ConcurrentHashMap<>();
    private final Map<String, NativeDataTypeDef> nativeTypeDefinitions = new ConcurrentHashMap<>();
    private final Map<String, String> unsupportedNativeTypes = new ConcurrentHashMap<>();
    /** Variables declared with an abstract DataType (BaseDataType/UInteger, IS-197): the
     * concrete type is carried per-value in a discriminated TREE rather than fixed by the schema. */
    private final Set<String> abstractTypeNodes = ConcurrentHashMap.newKeySet();
    private final ClientEventHub clientEventHub = new ClientEventHub();
    private final RuntimeEventHub runtimeEventHub = new RuntimeEventHub();
    /**
     * State for a single active fault entry. {@code delayMs} is only meaningful for
     * the {@code DELAY} fault kind; defaults to 100 ms when the caller omits the param.
     */
    record FaultState(boolean active, long delayMs) {
        static final long DEFAULT_DELAY_MS = 100L;
    }

    /**
     * Active faults keyed by {@code "kind:layer"} so the same fault kind can be
     * independently active at NEUTRAL vs PROTOCOL layer.
     */
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

    /** Test-visible endpoint of the currently configured simulated OPC UA server. */
    String opcUaEndpointUrl() {
        OpcUaServerRuntime runtime = serverRuntime.get();
        if (runtime == null) {
            throw new IllegalStateException("worker is not configured");
        }
        return runtime.endpointUrl();
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
        List<NativeDataTypeDef> typeDefinitions = new ArrayList<>();
        nodeDataTypes.clear();
        nodeArrayDimensions.clear();
        structureEncodings.clear();
        structureDataTypes.clear();
        treeDataTypes.clear();
        nativeTypeDefinitions.clear();
        unsupportedNativeTypes.clear();
        abstractTypeNodes.clear();
        for (SchemaNodeMsg node : request.getSchema().getNodesList()) {
            if ("VARIABLE".equals(node.getKind()) || "FOLDER".equals(node.getKind())
                    || "OBJECT".equals(node.getKind()) || "METHOD".equals(node.getKind())) {
                variables.add(new VarDef(node.getNodeId(), node.getParentId().isBlank() ? null : node.getParentId(),
                        node.getName(), node.getKind(), node.getDataType(), node.getDataTypeNodeId(),
                        node.getDeclaredDataTypeNodeId(), null,
                        null, null, null, null, node.getValueRank(), node.getArrayDimensionsList()));
            }
            if ("DATA_TYPE".equals(node.getKind())) {
                typeDefinitions.add(new NativeDataTypeDef(
                        node.getNodeId(),
                        node.getName(),
                        node.getDataTypeMembersList(),
                        node.getDataTypeEnumValuesList(),
                        node.getDataTypeDefaultEncodingId(), node.getNativeTypeKind()));
            }
        }
        typeDefinitions.forEach(definition -> nativeTypeDefinitions.put(definition.nodeId(), definition));
        Set<String> enumTypeIds = new HashSet<>();
        Map<String, NodeId> structureTypeEncodings = new HashMap<>();
        for (NativeDataTypeDef definition : typeDefinitions) {
            if (definition.isEnum()) {
                enumTypeIds.add(definition.nodeId());
            } else if (definition.isStructure() && definition.hasDefaultEncoding()) {
                try {
                    structureTypeEncodings.put(definition.nodeId(), NodeId.parse(definition.defaultEncodingId()));
                } catch (IllegalArgumentException ignored) {
                    // The declaration remains visible, but a malformed encoding id
                    // must not be guessed during replay.
                }
            }
        }
        for (SchemaNodeMsg node : request.getSchema().getNodesList()) {
            if (!"VARIABLE".equals(node.getKind())) {
                continue;
            }
            if ("ARRAY".equals(node.getValueRank())) {
                nodeArrayDimensions.put(node.getNodeId(), List.copyOf(node.getArrayDimensionsList()));
            }
            if (!node.getDataType().isBlank()) {
                nodeDataTypes.put(node.getNodeId(), node.getDataType());
            } else if (enumTypeIds.contains(node.getDataTypeNodeId())) {
                nodeDataTypes.put(node.getNodeId(), "INT32");
            } else if (structureTypeEncodings.containsKey(node.getDataTypeNodeId())) {
                structureEncodings.put(node.getNodeId(), structureTypeEncodings.get(node.getDataTypeNodeId()));
                structureDataTypes.put(node.getNodeId(), node.getDataTypeNodeId());
                treeDataTypes.put(node.getNodeId(), node.getDataTypeNodeId());
            } else if (nativeTypeDefinitions.containsKey(node.getDataTypeNodeId())
                    && nativeTypeDefinitions.get(node.getDataTypeNodeId()).isOptionSet()) {
                treeDataTypes.put(node.getNodeId(), node.getDataTypeNodeId());
            } else if (isAbstractDataType(node.getDataTypeNodeId())) {
                abstractTypeNodes.add(node.getNodeId());
            } else if (!node.getDataTypeNodeId().isBlank()) {
                unsupportedNativeTypes.put(node.getNodeId(), node.getDataTypeNodeId());
            }
        }
        String bindAddress = request.getOptions().getOrDefault("bindAddress", "127.0.0.1");
        String advertisedHost = request.getOptions().getOrDefault("advertisedHost", "127.0.0.1");
        AuthConfig auth = toAuthConfig(request.getSecurityConfig());
        serverRuntime.set(new OpcUaServerRuntime(
                request.getListenPort(), bindAddress, advertisedHost, variables, typeDefinitions, auth,
                clientEventHub::emit, runtimeEventHub::emit));
        configuredNodes.set(request.getSchema().getNodesCount());
        state.set("CONFIGURED");
        ackOk(obs, "configured " + variables.size() + " variables");
    }

    @Override
    public void testConnection(TestConnectionRequest request, StreamObserver<TestConnectionResponse> obs) {
        OpcUaDiscovery.ConnectionTest result =
                OpcUaDiscovery.testConnection(request.getEndpointUrl(), credentials(request.getCredentials()));
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
        OpcUaDiscovery.ScanOutcome outcome = OpcUaDiscovery.scan(
                request.getEndpointUrl(), credentials(request.getCredentials()), request.getMaxNodes(),
                () -> sendIfNotCancelled(obs, ScanEvent.newBuilder()
                        .setProgress(ScanProgress.newBuilder().setPhase("CONNECTED"))
                        .build()),
                soFar -> sendIfNotCancelled(obs, ScanEvent.newBuilder()
                        .setProgress(ScanProgress.newBuilder().setPhase("SCANNING").setDiscoveredSoFar(soFar))
                        .build()));
        // The client (supervisor) may have cancelled mid-scan (e.g. the caller gave up
        // or its own deadline fired) — the stream is already closed, so finishing it
        // here would throw. The browse already ran to completion/cap; its result is
        // simply discarded.
        if (obs.isCancelled()) {
            return;
        }
        // Send discovered nodes in bounded chunks rather than one ScanResponse.nodes
        // list, so a huge address space never produces a single message over gRPC's
        // max message size (previously hit RESOURCE_EXHAUSTED past ~a few 10k nodes).
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

    /** Nodes per {@link NodeBatch}; keeps each Scan stream message far under gRPC's 4MB default. */
    private static final int SCAN_NODE_BATCH_SIZE = 500;

    /** Best-effort send: a scan the client already cancelled must not throw on the browsing thread. */
    private static void sendIfNotCancelled(ServerCallStreamObserver<ScanEvent> obs, ScanEvent event) {
        if (!obs.isCancelled()) {
            obs.onNext(event);
        }
    }

    /**
     * Live capture (IS-045): client-mode subscription to a real source. Streams every
     * observed value change back as neutral {@link ValueBatch}es until the supervisor
     * cancels the call. The request schema names the variables to subscribe to and
     * carries each one's data type so values are encoded neutrally. No Configure/Start
     * — this is stateless client mode, like Scan. See openspec/specs/worker-contract/spec.md §6.
     */
    @Override
    public void capture(CaptureRequest request, StreamObserver<ValueBatch> responseObserver) {
        final List<OpcUaCapture.NodeSpec> nodes;
        try {
            nodes = captureNodes(request);
        } catch (IllegalArgumentException e) {
            responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription(e.getMessage())
                    .asRuntimeException());
            return;
        }
        ServerCallStreamObserver<ValueBatch> serverObserver =
                (ServerCallStreamObserver<ValueBatch>) responseObserver;
        // Register the cancel handler before starting so a cancel that races the
        // connect still tears the capture down. onNext is serialized on the observer.
        AtomicReference<OpcUaCapture> capture = new AtomicReference<>();
        serverObserver.setOnCancelHandler(() -> {
            OpcUaCapture c = capture.get();
            if (c != null) {
                c.stop();
            }
        });
        OpcUaDiscovery.Credentials creds = credentials(request.getCredentials());
        try {
            capture.set(OpcUaCapture.start(
                    request.getEndpointUrl(), creds.mode(), creds.username(), creds.secret(), nodes,
                    batch -> {
                        synchronized (serverObserver) {
                            if (!serverObserver.isCancelled()) {
                                responseObserver.onNext(ValueBatch.newBuilder().addAllValues(batch).build());
                            }
                        }
                    }));
            // Guard the connect window: if a cancel arrived while start() was
            // connecting, the cancel handler ran with a null reference, so stop the
            // now-started capture here instead of leaking the client and subscription.
            if (serverObserver.isCancelled()) {
                capture.get().stop();
            }
        } catch (Exception e) {
            OpcUaClientSupport.reinterruptIfNeeded(e);
            responseObserver.onError(Status.UNAVAILABLE
                    .withDescription(OpcUaClientSupport.rootMessage(e))
                    .asRuntimeException());
        }
    }

    /**
     * Builds capture specifications with an executable neutral value encoding.
     *
     * <p>Custom enum declarations have no primitive {@code data_type} on their
     * variable, but OPC UA encodes their values as integers.  Preserve that fact
     * here so the worker emits bytes that the supervisor can decode as
     * {@link ValueCodec.Kind#INT}.  A declaration with neither a primitive type
     * nor enum literals is opaque until structure encoding support is available;
     * accepting it would make {@link OpcUaCapture} guess an encoding from the Java
     * runtime value and silently corrupt capture/replay.
     */
    static List<OpcUaCapture.NodeSpec> captureNodes(CaptureRequest request) {
        Map<String, SchemaNodeMsg> declarations = new HashMap<>();
        for (SchemaNodeMsg node : request.getSchema().getNodesList()) {
            if ("DATA_TYPE".equals(node.getKind())) {
                declarations.put(node.getNodeId(), node);
            }
        }

        List<OpcUaCapture.NodeSpec> nodes = new ArrayList<>();
        for (SchemaNodeMsg node : request.getSchema().getNodesList()) {
            if (!"VARIABLE".equals(node.getKind())) {
                continue;
            }
            String dataType = node.getDataType();
            String defaultEncodingId = "";
            if (dataType.isEmpty() && !node.getDataTypeNodeId().isEmpty()) {
                SchemaNodeMsg declaration = declarations.get(node.getDataTypeNodeId());
                if (declaration != null && declaration.getDataTypeEnumValuesCount() > 0
                        && (declaration.getNativeTypeKind().isBlank()
                                || "ENUM".equals(declaration.getNativeTypeKind()))) {
                    dataType = "INT32";
                } else if (declaration != null && declaration.getDataTypeMembersCount() > 0
                        && !declaration.getDataTypeDefaultEncodingId().isEmpty()) {
                    defaultEncodingId = declaration.getDataTypeDefaultEncodingId();
                }
            }
            if (dataType.isEmpty() && defaultEncodingId.isEmpty()) {
                // A scanned endpoint can contain opaque native variables beside
                // ordinary scalar variables. They cannot be encoded without
                // guessing, but must not reject the whole capture request.
                continue;
            }
            nodes.add(new OpcUaCapture.NodeSpec(node.getNodeId(), dataType.isEmpty() ? null : dataType,
                    defaultEncodingId.isEmpty() ? null : defaultEncodingId,
                    "ARRAY".equals(node.getValueRank()), node.getArrayDimensionsList()));
        }
        return nodes;
    }

    /**
     * Worker → supervisor client-activity stream (IS-047): registers the supervisor's
     * observer with the {@link ClientEventHub} and leaves it open. The running OPC UA
     * server publishes a {@link ClientEvent} to the hub for each protocol client that
     * connects or disconnects; the stream ends when the supervisor cancels it.
     * See openspec/specs/worker-contract/spec.md.
     */
    @Override
    public void clientEvents(StreamRequest request, StreamObserver<ClientEvent> responseObserver) {
        clientEventHub.register((ServerCallStreamObserver<ClientEvent>) responseObserver);
    }

    /**
     * Worker → supervisor runtime-event stream (IS-048): registers the supervisor's
     * observer with the {@link RuntimeEventHub} and leaves it open. The running server
     * publishes SOURCE_START/SOURCE_STOP and value-apply failures publish ERROR; the
     * stream ends when the supervisor cancels it.
     * See openspec/specs/worker-contract/spec.md.
     */
    @Override
    public void runtimeEvents(StreamRequest request, StreamObserver<RuntimeEvent> responseObserver) {
        runtimeEventHub.register((ServerCallStreamObserver<RuntimeEvent>) responseObserver);
    }

    /**
     * Activates or clears a named fault (IS-088). Active faults are tracked in
     * {@link #activeFaults} and applied during value serving: {@code BAD_VALUE} and
     * {@code MISSING_VALUE} mark values with bad quality; {@code CONNECTION_DROP}
     * returns an error on value projection; {@code DELAY} adds artificial latency.
     * The RPC always acks success — fault state is advisory.
     */
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

    /**
     * Returns {@code true} when a fault of the given kind is active on ANY layer
     * (used by the projection path which is layer-agnostic at call time).
     */
    public boolean isFaultActive(String kind) {
        return activeFaults.keySet().stream().anyMatch(k -> k.startsWith(kind + ":"));
    }

    /** Returns the stored {@link FaultState} for the given compound key, or {@code null}. */
    FaultState faultState(String kind, String layer) {
        return activeFaults.get(kind + ":" + layer);
    }

    /** Maps the proto SecurityConfig to the worker-local AuthConfig (empty/default → anonymous). */
    private static AuthConfig toAuthConfig(SecurityConfig sc) {
        if (sc == null
                || (!sc.getAnonymousAllowed() && !sc.getUsernameEnabled() && sc.getUsersCount() == 0)) {
            return AuthConfig.anonymous();
        }
        Map<String, String> users = new HashMap<>();
        for (UserCredential u : sc.getUsersList()) {
            users.put(u.getUsername(), u.getPasswordHash());
        }
        return new AuthConfig(sc.getAnonymousAllowed(), sc.getUsernameEnabled(), users);
    }

    /** Maps the wire credential message to the discovery's session-only form. */
    private static OpcUaDiscovery.Credentials credentials(ConnectionConfigMsg cfg) {
        if (cfg == null || cfg.getMode().isEmpty()) {
            return new OpcUaDiscovery.Credentials("ANONYMOUS", null, null);
        }
        return new OpcUaDiscovery.Credentials(cfg.getMode(), cfg.getUsername(), cfg.getSecret());
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    @Override
    public void start(StartRequest request, StreamObserver<Ack> obs) {
        OpcUaServerRuntime runtime = serverRuntime.get();
        if (runtime != null) {
            try {
                runtime.start();
            } catch (BindFailedException e) {
                state.set("ERROR");
                obs.onNext(Ack.newBuilder().setOk(false).setMessage(e.getMessage()).build());
                obs.onCompleted();
                return;
            } catch (RuntimeException e) {
                state.set("ERROR");
                obs.onNext(Ack.newBuilder().setOk(false)
                        .setMessage("OPC UA server failed to start: " + e.getMessage()).build());
                obs.onCompleted();
                return;
            }
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

    /**
     * Graceful process exit, per openspec/specs/worker-contract/spec.md. Stops the
     * OPC UA runtime, acknowledges, then exits on a separate daemon thread so the
     * response has time to flush over gRPC before the process ends. The supervisor's
     * terminate-with-grace-then-kill remains the fallback if this does not happen fast
     * enough.
     */
    @Override
    public void shutdown(ShutdownRequest request, StreamObserver<Ack> obs) {
        OpcUaServerRuntime runtime = serverRuntime.get();
        if (runtime != null) {
            runtime.stop();
        }
        state.set("STOPPED");
        ackOk(obs, "shutting down");
        Thread exit = new Thread(() -> {
            try {
                // Empirically enough for gRPC to flush the ack over loopback. If interrupted,
                // exit immediately anyway; the supervisor's terminate-with-grace-then-kill
                // fallback covers the (rare) case where the ack hasn't flushed yet.
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
        // CONNECTION_DROP: treat the whole batch as a connection error — skip projection.
        if (isFaultActive("CONNECTION_DROP")) {
            runtimeEventHub.emit(RuntimeEvent.newBuilder()
                    .setType("ERROR")
                    .setAtMicros(System.currentTimeMillis() * 1_000L)
                    .setDetail("CONNECTION_DROP fault active — value batch dropped")
                    .build());
            return;
        }
        // DELAY: add artificial latency before projecting values.
        // Use the delay_ms stored in the fault state; fall back to the default if no
        // state is found (defensive: state should always exist when isFaultActive is true).
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
            // BAD_VALUE / MISSING_VALUE: skip projecting real values — OPC UA clients
            // receive the last good value (unchanged), simulating a bad-quality read.
            if (isFaultActive("BAD_VALUE") || isFaultActive("MISSING_VALUE")) {
                continue;
            }
            String dataType = nodeDataTypes.get(value.getNodeId());
            if (dataType != null) {
                try {
                    boolean array = nodeArrayDimensions.containsKey(value.getNodeId());
                    Object decoded = ValueCodec.decode(array ? ValueCodec.Kind.TREE : OpcUaTypes.codecKind(dataType),
                            value.getValueEnc().toByteArray());
                    runtime.updateValue(value.getNodeId(), array
                            ? OpcUaArrayValues.replayNeutral(dataType, decoded,
                                    nodeArrayDimensions.get(value.getNodeId()))
                            : OpcUaTypes.toOpcUaValue(dataType, decoded));
                } catch (RuntimeException e) {
                    runtimeEventHub.emit(RuntimeEvent.newBuilder()
                            .setType("ERROR")
                            .setAtMicros(System.currentTimeMillis() * 1_000L)
                            .setDetail("failed to apply value for node " + value.getNodeId()
                                    + ": " + e.getMessage())
                            .build());
                }
            } else if ("TREE".equals(value.getValueKind()) && treeDataTypes.containsKey(value.getNodeId())
                    && !nodeArrayDimensions.containsKey(value.getNodeId())) {
                Object decoded = ValueCodec.decode(ValueCodec.Kind.TREE, value.getValueEnc().toByteArray());
                runtime.updateValue(value.getNodeId(), treeNativeValue(
                        runtime, treeDataTypes.get(value.getNodeId()), decoded));
            } else if ("TREE".equals(value.getValueKind()) && abstractTypeNodes.contains(value.getNodeId())
                    && !nodeArrayDimensions.containsKey(value.getNodeId())) {
                Object decoded = ValueCodec.decode(ValueCodec.Kind.TREE, value.getValueEnc().toByteArray());
                runtime.updateValue(value.getNodeId(), OpcUaTypes.toOpcUaVariant(decoded));
            } else if (structureEncodings.containsKey(value.getNodeId())) {
                // A native structure is replayed as its original binary body. Its
                // encoding id is schema metadata, not inferred from the bytes.
                NodeId localEncoding = runtime.localEncodingId(structureDataTypes.get(value.getNodeId()));
                if (localEncoding == null) {
                    runtimeEventHub.emit(RuntimeEvent.newBuilder()
                            .setType("ERROR")
                            .setAtMicros(System.currentTimeMillis() * 1_000L)
                            .setDetail("native structure has no local runtime encoding: "
                                    + structureDataTypes.get(value.getNodeId()))
                            .build());
                    continue;
                }
                boolean array = nodeArrayDimensions.containsKey(value.getNodeId());
                Object decoded = array ? ValueCodec.decode(ValueCodec.Kind.TREE, value.getValueEnc().toByteArray()) : null;
                runtime.updateValue(value.getNodeId(), array
                        ? OpcUaArrayValues.replayStructure(decoded, localEncoding,
                                nodeArrayDimensions.get(value.getNodeId()))
                        : structureValue(localEncoding, value.getValueEnc().toByteArray()));
            } else if (unsupportedNativeTypes.containsKey(value.getNodeId())) {
                runtimeEventHub.emit(RuntimeEvent.newBuilder()
                        .setType("ERROR")
                        .setAtMicros(System.currentTimeMillis() * 1_000L)
                        .setDetail("cannot apply value for native DataType without an executable encoding: "
                                + unsupportedNativeTypes.get(value.getNodeId()))
                        .build());
            }
        }
    }

    /** Whether a declared DataType NodeId is one of the abstract roots (BaseDataType/UInteger,
     * IS-197) whose variables carry a dynamic concrete type per value. */
    private static boolean isAbstractDataType(String dataTypeNodeId) {
        if (dataTypeNodeId.isBlank()) {
            return false;
        }
        try {
            NodeId id = NodeId.parse(dataTypeNodeId);
            return Identifiers.BaseDataType.equals(id) || Identifiers.UInteger.equals(id);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Builds an opaque binary ExtensionObject without attempting to reinterpret its fields. */
    static ExtensionObject structureValue(NodeId defaultEncodingId, byte[] binaryBody) {
        if (defaultEncodingId == null || binaryBody == null) {
            throw new IllegalArgumentException("native structure requires a binary body and default encoding id");
        }
        return ExtensionObject.of(ByteString.of(binaryBody), defaultEncodingId);
    }

    /** Materializes a canonical TREE payload using the schema's exact native declarations. */
    private Object treeNativeValue(OpcUaServerRuntime runtime, String sourceTypeId, Object value) {
        NativeDataTypeDef definition = nativeTypeDefinitions.get(sourceTypeId);
        if (definition == null) {
            throw new IllegalArgumentException("native type declaration is missing: " + sourceTypeId);
        }
        if (definition.isOptionSet()) {
            if (!(value instanceof Map<?, ?> members)
                    || !(members.get("value") instanceof byte[] bits)
                    || !(members.get("validBits") instanceof byte[] validBits)) {
                throw new IllegalArgumentException("native option set value tree requires byte fields value and validBits");
            }
            return runtime.optionSetValue(sourceTypeId, bits, validBits);
        }
        if ("UNION".equals(definition.nativeTypeKind())) {
            if (!(value instanceof Map<?, ?> members) || members.size() != 1) {
                throw new IllegalArgumentException("native union value tree must contain exactly one field");
            }
            Map.Entry<?, ?> selected = members.entrySet().iterator().next();
            if (!(selected.getKey() instanceof String fieldName)) {
                throw new IllegalArgumentException("native union field name must be text");
            }
            return runtime.unionValue(sourceTypeId, fieldName,
                    treeMemberValue(runtime, member(definition, fieldName), selected.getValue()));
        }
        if (!(value instanceof Map<?, ?> members)) {
            throw new IllegalArgumentException("native structure value tree must be an object");
        }
        Map<String, Object> materialized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : members.entrySet()) {
            if (!(entry.getKey() instanceof String fieldName)) {
                throw new IllegalArgumentException("native structure field name must be text");
            }
            materialized.put(fieldName, treeMemberValue(runtime, member(definition, fieldName), entry.getValue()));
        }
        return runtime.structureValue(sourceTypeId, materialized);
    }

    private com.ainclusive.iotsim.workercontract.v1.DataTypeMemberMsg member(
            NativeDataTypeDef definition, String fieldName) {
        return definition.members().stream().filter(field -> field.getName().equals(fieldName)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "native type " + definition.name() + " has no field " + fieldName));
    }

    private Object treeMemberValue(OpcUaServerRuntime runtime,
            com.ainclusive.iotsim.workercontract.v1.DataTypeMemberMsg member, Object value) {
        if (!member.getDataTypeNodeId().isBlank()) {
            if ("ARRAY".equals(member.getValueRank())) {
                if (!(value instanceof List<?> values)) {
                    throw new IllegalArgumentException("native array field " + member.getName() + " must be an array");
                }
                return typedNativeArray(values.stream()
                        .map(item -> treeNativeValue(runtime, member.getDataTypeNodeId(), item)).toList());
            }
            return treeNativeValue(runtime, member.getDataTypeNodeId(), value);
        }
        if ("ARRAY".equals(member.getValueRank())) {
            if (!(value instanceof List<?> values)) {
                throw new IllegalArgumentException("native array field " + member.getName() + " must be an array");
            }
            return values.stream().map(item -> OpcUaTypes.toOpcUaValue(member.getDataType(), item))
                    .toArray(Object[]::new);
        }
        return OpcUaTypes.toOpcUaValue(member.getDataType(), value);
    }

    private static Object typedNativeArray(List<Object> values) {
        if (values.isEmpty()) {
            return new Object[0];
        }
        Object array = Array.newInstance(values.getFirst().getClass(), values.size());
        for (int i = 0; i < values.size(); i++) {
            Array.set(array, i, values.get(i));
        }
        return array;
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
