package com.ainclusive.iotsim.supervisor;

import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import com.ainclusive.iotsim.workercontract.v1.Quality;
import com.ainclusive.iotsim.workercontract.v1.Schema;
import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import com.ainclusive.iotsim.workercontract.v1.Value;
import com.google.protobuf.ByteString;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

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
 * {@code STARTING}; once the cap is exhausted it reports {@code ERROR}. The
 * health-monitoring loop and stale-state detection are a separate concern
 * (IS-041).
 *
 * <p>See backend-specs/02_WORKER_CONTRACT_AND_IPC.md §4.
 */
public final class Supervisor implements RuntimeController, AutoCloseable {

    private static final String RUNNING = "RUNNING";
    private static final String STOPPED = "STOPPED";
    private static final String STARTING = "STARTING";
    private static final String ERROR = "ERROR";
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(10);

    private final WorkerLauncher launcher;
    private final RestartPolicy restartPolicy;
    private final ScheduledExecutorService scheduler;
    private final Map<String, ManagedWorker> running = new ConcurrentHashMap<>();
    private volatile boolean closed;

    public Supervisor(WorkerLauncher launcher) {
        this(launcher, RestartPolicy.DEFAULT);
    }

    public Supervisor(WorkerLauncher launcher, RestartPolicy restartPolicy) {
        this.launcher = launcher;
        this.restartPolicy = restartPolicy;
        AtomicInteger seq = new AtomicInteger();
        this.scheduler = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r, "worker-restart-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
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
        running.compute(dataSourceId, (id, existing) -> {
            if (existing != null && existing.isActive()) {
                return existing;
            }
            ManagedWorker worker = new ManagedWorker(id, spec);
            worker.launchAndStart();
            return worker;
        });
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

    private static Schema toProtoSchema(RuntimeStartSpec spec) {
        Schema.Builder schema = Schema.newBuilder().setVersion(spec.schemaVersion());
        for (SchemaNode n : spec.schemaNodes()) {
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
        private volatile WorkerState state = WorkerState.SPAWNED;

        ManagedWorker(String dataSourceId, RuntimeStartSpec spec) {
            this.dataSourceId = dataSourceId;
            this.spec = spec;
        }

        /** Initial bring-up, synchronous so the caller of {@code start()} sees failures. */
        synchronized void launchAndStart() {
            connectAndStart();
        }

        /** Caller holds the monitor. Spawns, handshakes, configures and starts a worker. */
        private void connectAndStart() {
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
                newClient.configure(toProtoSchema(spec), spec.listenPort());
                newClient.start();
            } catch (RuntimeException e) {
                newClient.close();
                newLaunched.close();
                throw e;
            }
            this.launched = newLaunched;
            this.client = newClient;
            this.state = WorkerState.RUNNING;
            // Watch THIS launch; a stale notification from a superseded launch is ignored.
            newLaunched.onExit().whenComplete((v, t) -> onWorkerExit(newLaunched));
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

        private synchronized void restart() {
            if (stopping) {
                return;
            }
            pendingRestart = null;
            try {
                connectAndStart();
            } catch (RuntimeException e) {
                // Relaunch itself failed; treat as another failure and back off again.
                scheduleRestart();
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
            if (toCancel != null) {
                toCancel.cancel(false);
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

        /** Still running or recovering — i.e. not a worker that exhausted its restart budget. */
        boolean isActive() {
            return state != WorkerState.EXITED;
        }

        String stateName() {
            return switch (state) {
                case RUNNING -> RUNNING;
                case EXITED -> ERROR;
                case STOPPED -> STOPPED;
                default -> STARTING; // SPAWNED / READY / CONFIGURED: starting or restarting
            };
        }
    }
}
