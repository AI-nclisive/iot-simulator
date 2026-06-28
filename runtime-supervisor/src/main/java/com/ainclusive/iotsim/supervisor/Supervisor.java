package com.ainclusive.iotsim.supervisor;

import com.ainclusive.iotsim.platform.capture.CaptureException;
import com.ainclusive.iotsim.platform.capture.CaptureSession;
import com.ainclusive.iotsim.platform.capture.CaptureSpec;
import com.ainclusive.iotsim.platform.capture.SourceCapturer;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.platform.scan.ConnectionTestResult;
import com.ainclusive.iotsim.platform.scan.DiscoveredNode;
import com.ainclusive.iotsim.platform.scan.ScanResult;
import com.ainclusive.iotsim.platform.scan.ScanSpec;
import com.ainclusive.iotsim.platform.scan.ScanStatus;
import com.ainclusive.iotsim.platform.scan.SourceScanner;
import com.ainclusive.iotsim.platform.secret.ConnectionCredentials;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import com.ainclusive.iotsim.workercontract.v1.CaptureRequest;
import com.ainclusive.iotsim.workercontract.v1.ConnectionConfigMsg;
import com.ainclusive.iotsim.workercontract.v1.Quality;
import com.ainclusive.iotsim.workercontract.v1.ScanRequest;
import com.ainclusive.iotsim.workercontract.v1.ScanResponse;
import com.ainclusive.iotsim.workercontract.v1.Schema;
import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import com.ainclusive.iotsim.workercontract.v1.TestConnectionRequest;
import com.ainclusive.iotsim.workercontract.v1.TestConnectionResponse;
import com.ainclusive.iotsim.workercontract.v1.Value;
import com.ainclusive.iotsim.workercontract.v1.ValueBatch;
import com.google.protobuf.ByteString;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Owns worker lifecycle: port allocation, launch, IPC handshake, start/stop,
 * tracking, and restart-with-backoff on unexpected failure. Protocol-agnostic —
 * adding a protocol means adding a worker, not changing the supervisor.
 * Implements the {@link RuntimeController} port so the domain can drive runtime
 * without depending on this module.
 *
 * <p>Only <em>unexpected</em> worker exits are restarted (exponential backoff up
 * to a cap, per {@link RestartPolicy}); an intentional {@code stop()} and worker
 * faults are never auto-healed. While a worker is recovering the source reports
 * {@code STARTING}; once the cap is exhausted it reports {@code ERROR}.
 *
 * <p>A separate health-monitoring loop probes each {@code RUNNING} worker's
 * {@code Health} RPC on a fixed interval (per {@link HealthPolicy}). A worker that
 * is alive but stops answering — a hang that never fires a process exit — is
 * reported as {@code STALE}; a good probe clears it back to {@code RUNNING}. This
 * is propagation only: stale detection reflects worker health to the API/UI and
 * does not itself restart the worker.
 *
 * <p>See backend-specs/02_WORKER_CONTRACT_AND_IPC.md §4.
 */
public final class Supervisor implements RuntimeController, SourceScanner, SourceCapturer, AutoCloseable {

    private static final String RUNNING = "RUNNING";
    private static final String STOPPED = "STOPPED";
    private static final String STARTING = "STARTING";
    private static final String ERROR = "ERROR";
    private static final String STALE = "STALE";
    private static final String OPC_UA = "OPC_UA";
    private static final String EXTERNAL_REF_UNSUPPORTED =
            "external-ref credential resolution is not yet supported for real-source access (IS-082); "
                    + "use anonymous or password credentials";
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(10);

    private final WorkerLauncher launcher;
    private final RestartPolicy restartPolicy;
    private final HealthPolicy healthPolicy;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ManagedWorker> running = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public Supervisor(WorkerLauncher launcher) {
        this(launcher, RestartPolicy.DEFAULT, HealthPolicy.DEFAULT);
    }

    public Supervisor(WorkerLauncher launcher, RestartPolicy restartPolicy) {
        this(launcher, restartPolicy, HealthPolicy.DEFAULT);
    }

    public Supervisor(WorkerLauncher launcher, RestartPolicy restartPolicy, HealthPolicy healthPolicy) {
        this.launcher = launcher;
        this.restartPolicy = restartPolicy;
        this.healthPolicy = healthPolicy;
        AtomicInteger seq = new AtomicInteger();
        // One pool serves restarts and the health-monitoring loop; sized so a probe
        // (bounded by HealthPolicy.probeTimeout) cannot starve a pending restart.
        this.scheduler = Executors.newScheduledThreadPool(3, r -> {
            Thread t = new Thread(r, "worker-supervisor-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        long periodMillis = healthPolicy.pollInterval().toMillis();
        scheduler.scheduleWithFixedDelay(
                this::pollHealth, periodMillis, periodMillis, TimeUnit.MILLISECONDS);
    }

    /**
     * Health-monitoring loop body: probes every tracked worker so an alive-but-hung
     * worker (one that never fires an exit) is reflected as {@code STALE} to the
     * API/UI. One worker's probe failure must not stop the others, so each is
     * isolated.
     */
    private void pollHealth() {
        if (closed) {
            return;
        }
        for (ManagedWorker worker : running.values()) {
            try {
                worker.probeHealth();
            } catch (RuntimeException ignored) {
                // best effort; a single bad probe must not kill the loop
            }
        }
    }

    @Override
    public String start(String dataSourceId, RuntimeStartSpec spec) {
        if (closed) {
            throw new IllegalStateException("supervisor is closed");
        }
        // launchAndStart() throws on failure, so compute leaves the map unchanged and
        // the caller sees the launch error directly. A worker still active (RUNNING or
        // recovering) is left as-is — start is idempotent. A worker that exhausted its
        // restart budget (ERROR) is replaced, so an operator can explicitly retry it.
        // Only an exit after a successful start is recovered via restart-with-backoff.
        AtomicReference<ManagedWorker> launched = new AtomicReference<>();
        running.compute(dataSourceId, (id, existing) -> {
            if (existing != null && existing.isActive()) {
                return existing;
            }
            ManagedWorker worker = new ManagedWorker(id, spec);
            worker.launchAndStart();
            launched.set(worker);
            return worker;
        });
        // close() can race in after the guard above: it sets `closed`, then drains the
        // map. If we just launched a worker, re-check and tear it down ourselves so it
        // can never be orphaned — whichever of close()/start() observes the other,
        // the worker is stopped exactly by one path (stop() is idempotent).
        ManagedWorker mine = launched.get();
        if (mine != null && closed) {
            running.remove(dataSourceId, mine);
            mine.stop();
            throw new IllegalStateException("supervisor is closed");
        }
        return RUNNING;
    }

    @Override
    public String stop(String dataSourceId) {
        ManagedWorker worker = running.remove(dataSourceId);
        if (worker != null) {
            worker.stop();
        }
        return STOPPED;
    }

    @Override
    public String state(String dataSourceId) {
        ManagedWorker worker = running.get(dataSourceId);
        return worker == null ? STOPPED : worker.stateName();
    }

    @Override
    public long applyValues(String dataSourceId, List<NeutralValue> values) {
        ManagedWorker worker = running.get(dataSourceId);
        if (worker == null) {
            throw new IllegalStateException("data source is not running: " + dataSourceId);
        }
        List<Value> protoValues = values.stream().map(Supervisor::toProto).toList();
        return worker.applyValues(protoValues);
    }

    /**
     * Real-source discovery (create-from-scan). Spawns a one-shot worker in client
     * mode, probes the endpoint, and tears the worker down — it is never adopted
     * into the managed {@code running} map. Only OPC UA is supported today; Modbus
     * discovery lands with worker-modbus. See backend-specs/02 §6 / 05 §Scan.
     */
    @Override
    public ConnectionTestResult testConnection(ScanSpec spec) {
        if (!OPC_UA.equals(spec.protocol())) {
            return new ConnectionTestResult(ScanStatus.UNSUPPORTED, unsupportedMessage(spec.protocol()));
        }
        if (isExternalRef(spec)) {
            return new ConnectionTestResult(ScanStatus.UNSUPPORTED, EXTERNAL_REF_UNSUPPORTED);
        }
        return withWorker(spec.protocol(), client -> {
            TestConnectionResponse response = client.testConnection(TestConnectionRequest.newBuilder()
                    .setEndpointUrl(orEmpty(spec.endpointUrl()))
                    .setCredentials(toCredentialMsg(spec.credentials()))
                    .build());
            return new ConnectionTestResult(toStatus(response.getStatus()), response.getMessage());
        });
    }

    @Override
    public ScanResult scan(ScanSpec spec) {
        if (!OPC_UA.equals(spec.protocol())) {
            return ScanResult.failure(ScanStatus.UNSUPPORTED, unsupportedMessage(spec.protocol()));
        }
        if (isExternalRef(spec)) {
            return ScanResult.failure(ScanStatus.UNSUPPORTED, EXTERNAL_REF_UNSUPPORTED);
        }
        return withWorker(spec.protocol(), client -> {
            ScanResponse response = client.scan(ScanRequest.newBuilder()
                    .setEndpointUrl(orEmpty(spec.endpointUrl()))
                    .setCredentials(toCredentialMsg(spec.credentials()))
                    .setMaxNodes(spec.maxNodes())
                    .build());
            return toScanResult(response);
        });
    }

    /**
     * Live capture from a running real source (record real data, IS-045). Spawns a
     * long-lived worker in client mode that subscribes to the source's schema
     * variables and streams observed value changes back; each batch is decoded
     * against the schema's types and handed to {@code sink}. The returned session
     * stops the stream (firing the worker's cancel handler) and tears the worker
     * down. Only OPC UA is supported today. See backend-specs/02 §6.
     */
    @Override
    public CaptureSession startCapture(CaptureSpec spec, Consumer<List<NeutralValue>> sink) {
        if (closed) {
            throw new IllegalStateException("supervisor is closed");
        }
        if (!OPC_UA.equals(spec.protocol())) {
            throw new CaptureException(CaptureException.Kind.UNSUPPORTED, unsupportedMessage(spec.protocol()));
        }
        if (isExternalRef(spec.credentials())) {
            throw new CaptureException(CaptureException.Kind.UNSUPPORTED, EXTERNAL_REF_UNSUPPORTED);
        }
        Map<String, ValueCodec.Kind> kinds = new HashMap<>();
        for (SchemaNode n : spec.schemaNodes()) {
            if (n.kind() == NodeKind.VARIABLE && n.dataType() != null) {
                kinds.put(n.nodeId(), ValueCodec.kindOf(n.dataType()));
            }
        }
        CaptureRequest request = CaptureRequest.newBuilder()
                .setEndpointUrl(orEmpty(spec.endpointUrl()))
                .setCredentials(toCredentialMsg(spec.credentials()))
                .setSchema(toProtoSchema(spec.schemaVersion(), spec.schemaNodes()))
                .build();

        int controlPort = PortAllocator.freeLoopbackPort();
        LaunchedWorker launched;
        try {
            launched = launcher.launch(spec.protocol(), controlPort);
        } catch (Exception e) {
            throw new CaptureException(
                    CaptureException.Kind.UNAVAILABLE, "failed to launch " + spec.protocol() + " worker", e);
        }
        WorkerClient client = new WorkerClient("127.0.0.1", controlPort);
        try {
            awaitReady(client);
            WorkerClient.CaptureHandle handle = client.capture(
                    request,
                    batch -> {
                        List<NeutralValue> values = toNeutralValues(batch, kinds);
                        if (!values.isEmpty()) {
                            sink.accept(values);
                        }
                    },
                    error -> {
                        // Stream errors (incl. CANCELLED on stop) end the session; the
                        // session owner tears the worker down via stop().
                    });
            return new SupervisorCaptureSession(handle, client, launched);
        } catch (RuntimeException e) {
            closeQuietly(client);
            launched.close();
            throw new CaptureException(CaptureException.Kind.UNAVAILABLE, "failed to start capture", e);
        }
    }

    /** Spawns a worker, runs one RPC against it, and always tears it back down. */
    private <T> T withWorker(String protocol, Function<WorkerClient, T> call) {
        if (closed) {
            throw new IllegalStateException("supervisor is closed");
        }
        int controlPort = PortAllocator.freeLoopbackPort();
        LaunchedWorker launched;
        try {
            launched = launcher.launch(protocol, controlPort);
        } catch (Exception e) {
            throw new WorkerLaunchException("failed to launch " + protocol + " worker", e);
        }
        WorkerClient client = new WorkerClient("127.0.0.1", controlPort);
        try {
            awaitReady(client);
            return call.apply(client);
        } finally {
            closeQuietly(client);
            launched.close();
        }
    }

    private static boolean isExternalRef(ScanSpec spec) {
        return isExternalRef(spec.credentials());
    }

    private static boolean isExternalRef(ConnectionCredentials credentials) {
        return credentials != null && credentials.mode() == ConnectionCredentials.Mode.EXTERNAL_REF;
    }

    /** Decodes a captured value batch into neutral values using the schema's types. */
    private static List<NeutralValue> toNeutralValues(
            ValueBatch batch, Map<String, ValueCodec.Kind> kinds) {
        List<NeutralValue> out = new ArrayList<>(batch.getValuesCount());
        for (Value v : batch.getValuesList()) {
            ValueCodec.Kind kind = kinds.get(v.getNodeId());
            if (kind == null) {
                continue; // a value for a node not in the recording's schema; skip
            }
            Object value = ValueCodec.decode(kind, v.getValueEnc().toByteArray());
            long micros = v.getSourceTimeMicros();
            Instant sourceTime = Instant.ofEpochSecond(
                    Math.floorDiv(micros, 1_000_000L), Math.floorMod(micros, 1_000_000L) * 1_000L);
            String reason = v.getQualityReason().isEmpty() ? null : v.getQualityReason();
            out.add(new NeutralValue(v.getNodeId(), sourceTime, value, neutralQuality(v.getQuality()), reason));
        }
        return out;
    }

    private static com.ainclusive.iotsim.protocolmodel.Quality neutralQuality(Quality wire) {
        return switch (wire) {
            case UNCERTAIN -> com.ainclusive.iotsim.protocolmodel.Quality.UNCERTAIN;
            case BAD -> com.ainclusive.iotsim.protocolmodel.Quality.BAD;
            default -> com.ainclusive.iotsim.protocolmodel.Quality.GOOD; // GOOD, UNSPECIFIED, UNRECOGNIZED
        };
    }

    /** One live-capture session: cancels the worker stream and tears the worker down on stop. */
    private static final class SupervisorCaptureSession implements CaptureSession {

        private final WorkerClient.CaptureHandle handle;
        private final WorkerClient client;
        private final LaunchedWorker launched;
        private final AtomicBoolean stopped = new AtomicBoolean();

        SupervisorCaptureSession(
                WorkerClient.CaptureHandle handle, WorkerClient client, LaunchedWorker launched) {
            this.handle = handle;
            this.client = client;
            this.launched = launched;
        }

        @Override
        public void stop() {
            if (stopped.compareAndSet(false, true)) {
                handle.cancel();
                closeQuietly(client);
                launched.close();
            }
        }
    }

    private static ConnectionConfigMsg toCredentialMsg(ConnectionCredentials credentials) {
        if (credentials == null) {
            return ConnectionConfigMsg.newBuilder().setMode("ANONYMOUS").build();
        }
        ConnectionConfigMsg.Builder b = ConnectionConfigMsg.newBuilder().setMode(credentials.mode().name());
        if (credentials.username() != null) {
            b.setUsername(credentials.username());
        }
        if (credentials.secret() != null) {
            b.setSecret(credentials.secret());
        }
        if (credentials.secretRef() != null) {
            b.setSecretRef(credentials.secretRef());
        }
        return b.build();
    }

    private static ScanResult toScanResult(ScanResponse response) {
        List<DiscoveredNode> nodes = response.getNodesList().stream()
                .map(Supervisor::toDiscoveredNode).toList();
        return new ScanResult(toStatus(response.getStatus()), nodes,
                response.getTruncated(), response.getUnknownCount(), response.getMessage());
    }

    private static DiscoveredNode toDiscoveredNode(SchemaNodeMsg n) {
        return new DiscoveredNode(
                n.getNodeId(), emptyToNull(n.getParentId()), n.getPath(), n.getName(), n.getKind(),
                emptyToNull(n.getDataType()), emptyToNull(n.getValueRank()), emptyToNull(n.getAccess()),
                emptyToNull(n.getUnit()), emptyToNull(n.getDescription()));
    }

    private static ScanStatus toStatus(String wire) {
        try {
            return ScanStatus.valueOf(wire);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ScanStatus.UNREACHABLE;
        }
    }

    private static String unsupportedMessage(String protocol) {
        return "scanning is not supported for protocol " + protocol;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    /** Stops every worker and the restart scheduler; safe to call on app shutdown. */
    @Override
    public void close() {
        closed = true;
        scheduler.shutdownNow();
        running.values().forEach(ManagedWorker::stop);
        running.clear();
    }

    private static Value toProto(NeutralValue nv) {
        ValueCodec.Encoded enc = ValueCodec.encode(nv.value());
        long micros = nv.sourceTime().getEpochSecond() * 1_000_000L + nv.sourceTime().getNano() / 1_000;
        return Value.newBuilder()
                .setNodeId(nv.nodeId())
                .setSourceTimeMicros(micros)
                .setValueEnc(ByteString.copyFrom(enc.bytes()))
                .setQuality(Quality.valueOf(nv.quality().name()))
                .setQualityReason(nv.qualityReason() == null ? "" : nv.qualityReason())
                .build();
    }

    private static Schema toProtoSchema(int version, List<SchemaNode> nodes) {
        Schema.Builder schema = Schema.newBuilder().setVersion(version);
        for (SchemaNode n : nodes) {
            schema.addNodes(SchemaNodeMsg.newBuilder()
                    .setNodeId(n.nodeId())
                    .setParentId(orEmpty(n.parentId()))
                    .setPath(n.path())
                    .setName(n.name())
                    .setKind(n.kind().name())
                    .setDataType(n.dataType() == null ? "" : n.dataType().name())
                    .setValueRank(n.valueRank() == null ? "" : n.valueRank().name())
                    .setAccess(n.access() == null ? "" : n.access().name())
                    .setUnit(orEmpty(n.unit()))
                    .setDescription(orEmpty(n.description()))
                    .build());
        }
        return schema.build();
    }

    private static String orEmpty(String value) {
        return value == null ? "" : value;
    }

    private static void awaitReady(WorkerClient client) {
        long deadline = System.nanoTime() + READY_TIMEOUT.toNanos();
        RuntimeException last = null;
        while (System.nanoTime() < deadline) {
            try {
                client.hello();
                return;
            } catch (RuntimeException e) {
                last = e;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new WorkerLaunchException("interrupted while waiting for worker", ie);
                }
            }
        }
        throw new WorkerLaunchException("worker did not become ready in time", last);
    }

    private static void closeQuietly(WorkerClient client) {
        if (client != null) {
            try {
                client.close();
            } catch (RuntimeException ignored) {
                // best effort; we are tearing down
            }
        }
    }

    /** A live worker built by {@code connect()} but not yet adopted as the managed worker. */
    private record Connection(LaunchedWorker launched, WorkerClient client) {

        void close() {
            closeQuietly(client);
            launched.close();
        }
    }

    /**
     * One data-source's worker across its (possibly restarted) lifetime. Holds the
     * start spec so a crashed worker can be relaunched, the current launch/client,
     * and the restart bookkeeping. All mutable fields are guarded by the monitor;
     * {@link #state} is volatile so {@link #stateName()} reads it lock-free.
     */
    private final class ManagedWorker {

        private final String dataSourceId;
        private final RuntimeStartSpec spec;

        private LaunchedWorker launched;
        private WorkerClient client;
        private int restarts;
        private boolean stopping;
        private ScheduledFuture<?> pendingRestart;
        private int healthFailures;
        private volatile WorkerState state = WorkerState.SPAWNED;
        private volatile boolean stale;

        ManagedWorker(String dataSourceId, RuntimeStartSpec spec) {
            this.dataSourceId = dataSourceId;
            this.spec = spec;
        }

        /** Initial bring-up, synchronous so the caller of {@code start()} sees failures. */
        void launchAndStart() {
            // connect() blocks (spawn + handshake); the worker isn't reachable by stop()
            // until installed, so no monitor is needed around the launch itself.
            Connection conn = connect();
            synchronized (this) {
                install(conn);
            }
        }

        /**
         * Spawns, handshakes, configures and starts a worker. Blocking and lock-free —
         * a concurrent {@code stop()} stays responsive instead of waiting out the
         * handshake. Returns the live connection or throws and leaves nothing running.
         */
        private Connection connect() {
            int controlPort = PortAllocator.freeLoopbackPort();
            LaunchedWorker newLaunched;
            try {
                newLaunched = launcher.launch(spec.protocol(), controlPort);
            } catch (Exception e) {
                throw new WorkerLaunchException("failed to launch " + spec.protocol() + " worker", e);
            }
            WorkerClient newClient = new WorkerClient("127.0.0.1", controlPort);
            try {
                awaitReady(newClient);
                newClient.configure(
                        toProtoSchema(spec.schemaVersion(), spec.schemaNodes()), spec.listenPort());
                newClient.start();
            } catch (RuntimeException e) {
                newClient.close();
                newLaunched.close();
                throw e;
            }
            return new Connection(newLaunched, newClient);
        }

        /** Caller holds the monitor. Adopts a freshly built connection as the live worker. */
        private void install(Connection conn) {
            this.launched = conn.launched();
            this.client = conn.client();
            this.state = WorkerState.RUNNING;
            // A fresh worker starts healthy: drop any staleness carried over from the
            // launch it replaced so the health loop judges this one on its own probes.
            this.healthFailures = 0;
            this.stale = false;
            // Watch THIS launch; a stale notification from a superseded launch is ignored.
            conn.launched().onExit().whenComplete((v, t) -> onWorkerExit(conn.launched()));
        }

        /** Fires when a worker process exits — schedules a restart unless it was intentional. */
        private synchronized void onWorkerExit(LaunchedWorker exited) {
            if (stopping || launched != exited) {
                return;
            }
            // The process is gone; release its client channel before relaunching.
            closeQuietly(client);
            client = null;
            launched = null;
            scheduleRestart();
        }

        /** Caller holds the monitor. Backs off and schedules a restart, or gives up at the cap. */
        private void scheduleRestart() {
            if (restarts >= restartPolicy.maxRestarts()) {
                state = WorkerState.EXITED; // exhausted -> reported as ERROR
                return;
            }
            restarts++;
            state = WorkerState.SPAWNED; // recovering -> reported as STARTING
            Duration delay = restartPolicy.backoffFor(restarts);
            try {
                pendingRestart = scheduler.schedule(
                        this::restart, delay.toMillis(), TimeUnit.MILLISECONDS);
            } catch (RejectedExecutionException e) {
                state = WorkerState.EXITED; // scheduler shutting down; nothing more to try
            }
        }

        private void restart() {
            synchronized (this) {
                if (stopping) {
                    return;
                }
            }
            Connection conn;
            try {
                conn = connect(); // blocking, no monitor — stop() can interrupt via cancel(true)
            } catch (RuntimeException e) {
                // Relaunch itself failed (or was interrupted by stop()); back off again
                // unless we are tearing down.
                synchronized (this) {
                    if (!stopping) {
                        scheduleRestart();
                    }
                }
                return;
            }
            synchronized (this) {
                if (stopping) {
                    // stop() raced in during the handshake — discard the fresh worker.
                    conn.close();
                    return;
                }
                install(conn);
            }
        }

        void stop() {
            ScheduledFuture<?> toCancel;
            LaunchedWorker toClose;
            WorkerClient clientToClose;
            synchronized (this) {
                stopping = true;
                state = WorkerState.STOPPED;
                toCancel = pendingRestart;
                pendingRestart = null;
                toClose = launched;
                clientToClose = client;
                launched = null;
                client = null;
            }
            // Tear down outside the lock; onWorkerExit will see stopping=true and skip restart.
            // cancel(true) also interrupts a restart whose handshake is already in flight,
            // so stop() returns promptly instead of waiting out the ready-timeout.
            if (toCancel != null) {
                toCancel.cancel(true);
            }
            if (clientToClose != null) {
                try {
                    clientToClose.stop();
                } catch (RuntimeException ignored) {
                    // best effort; we are tearing down
                }
                clientToClose.close();
            }
            if (toClose != null) {
                toClose.close();
            }
        }

        long applyValues(List<Value> protoValues) {
            WorkerClient current;
            synchronized (this) {
                current = client;
            }
            if (current == null) {
                throw new IllegalStateException("data source is not running: " + dataSourceId);
            }
            return current.applyValues(protoValues);
        }

        /**
         * One health-monitoring tick. Probes the worker's {@code Health} RPC off the
         * monitor (it can block up to the probe timeout) and folds the result back in:
         * a worker that misses {@code staleThreshold} consecutive probes is marked
         * {@code stale}; a single good probe clears it. Only a steady-state
         * {@code RUNNING} worker is probed — a stopping, recovering, or exited worker
         * has no live worker to ask, and its non-RUNNING lifecycle state already drives
         * {@link #stateName()}.
         */
        void probeHealth() {
            WorkerClient current;
            synchronized (this) {
                if (stopping || state != WorkerState.RUNNING || client == null) {
                    return;
                }
                current = client;
            }
            boolean healthy = current.isHealthy(healthPolicy.probeTimeout());
            synchronized (this) {
                // The worker may have stopped or been restarted while we probed; if the
                // live client changed, our verdict is about a now-defunct worker — drop it.
                if (stopping || state != WorkerState.RUNNING || client != current) {
                    return;
                }
                if (healthy) {
                    healthFailures = 0;
                    stale = false;
                } else if (++healthFailures >= healthPolicy.staleThreshold()) {
                    stale = true;
                }
            }
        }

        /** Still running or recovering — i.e. not a worker that exhausted its restart budget. */
        boolean isActive() {
            return state != WorkerState.EXITED;
        }

        String stateName() {
            return switch (state) {
                // A live worker that has stopped answering health probes is alive-but-stuck:
                // surface it as STALE rather than a healthy RUNNING.
                case RUNNING -> stale ? STALE : RUNNING;
                case EXITED -> ERROR;
                case STOPPED -> STOPPED;
                default -> STARTING; // SPAWNED / READY / CONFIGURED: starting or restarting
            };
        }
    }
}
