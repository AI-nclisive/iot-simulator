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
import java.util.ArrayList;
import java.util.HashMap;
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

    private static final int SHUTDOWN_FLUSH_DELAY_MS = 200;

    private final AtomicReference<String> state = new AtomicReference<>("READY");
    private final AtomicLong applied = new AtomicLong();
    private final AtomicInteger configuredNodes = new AtomicInteger();
    private final AtomicReference<OpcUaServerRuntime> serverRuntime = new AtomicReference<>();
    private final Map<String, String> nodeDataTypes = new ConcurrentHashMap<>();
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
            if ("VARIABLE".equals(node.getKind()) || "FOLDER".equals(node.getKind())) {
                variables.add(new VarDef(node.getNodeId(), node.getParentId().isBlank() ? null : node.getParentId(),
                        node.getName(), node.getKind(), node.getDataType()));
            }
            if ("VARIABLE".equals(node.getKind())) {
                nodeDataTypes.put(node.getNodeId(), node.getDataType());
            }
        }
        String bindAddress = request.getOptions().getOrDefault("bindAddress", "127.0.0.1");
        String advertisedHost = request.getOptions().getOrDefault("advertisedHost", "127.0.0.1");
        AuthConfig auth = toAuthConfig(request.getSecurityConfig());
        serverRuntime.set(new OpcUaServerRuntime(
                request.getListenPort(), bindAddress, advertisedHost, variables, auth,
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
     * — this is stateless client mode, like Scan. See backend-specs/02 §6.
     */
    @Override
    public void capture(CaptureRequest request, StreamObserver<ValueBatch> responseObserver) {
        List<OpcUaCapture.NodeSpec> nodes = new ArrayList<>();
        for (SchemaNodeMsg node : request.getSchema().getNodesList()) {
            if ("VARIABLE".equals(node.getKind())) {
                nodes.add(new OpcUaCapture.NodeSpec(node.getNodeId(), node.getDataType()));
            }
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
     * Worker → supervisor client-activity stream (IS-047): registers the supervisor's
     * observer with the {@link ClientEventHub} and leaves it open. The running OPC UA
     * server publishes a {@link ClientEvent} to the hub for each protocol client that
     * connects or disconnects; the stream ends when the supervisor cancels it.
     * See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
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
     * See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
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
     * Graceful process exit, per backend-specs/02_WORKER_CONTRACT_AND_IPC.md. Stops the
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
                    Object decoded = ValueCodec.decode(
                            OpcUaTypes.codecKind(dataType), value.getValueEnc().toByteArray());
                    runtime.updateValue(value.getNodeId(), OpcUaTypes.toOpcUaValue(dataType, decoded));
                } catch (RuntimeException e) {
                    runtimeEventHub.emit(RuntimeEvent.newBuilder()
                            .setType("ERROR")
                            .setAtMicros(System.currentTimeMillis() * 1_000L)
                            .setDetail("failed to apply value for node " + value.getNodeId()
                                    + ": " + e.getMessage())
                            .build());
                }
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
