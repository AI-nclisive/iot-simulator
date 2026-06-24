package com.epam.iotsim.supervisor;

import com.epam.iotsim.platform.runtime.RuntimeController;
import com.epam.iotsim.platform.runtime.RuntimeStartSpec;
import com.epam.iotsim.protocolmodel.NeutralValue;
import com.epam.iotsim.protocolmodel.SchemaNode;
import com.epam.iotsim.protocolmodel.ValueCodec;
import com.epam.iotsim.workercontract.v1.Quality;
import com.epam.iotsim.workercontract.v1.Schema;
import com.epam.iotsim.workercontract.v1.SchemaNodeMsg;
import com.epam.iotsim.workercontract.v1.Value;
import com.google.protobuf.ByteString;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns worker lifecycle: port allocation, launch, IPC handshake, start/stop and
 * tracking. Protocol-agnostic — adding a protocol means adding a worker, not
 * changing the supervisor. Implements the {@link RuntimeController} port so the
 * domain can drive runtime without depending on this module.
 *
 * <p>See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
public final class Supervisor implements RuntimeController {

    private static final String RUNNING = "RUNNING";
    private static final String STOPPED = "STOPPED";
    private static final Duration READY_TIMEOUT = Duration.ofSeconds(10);

    private final WorkerLauncher launcher;
    private final Map<String, RunningWorker> running = new ConcurrentHashMap<>();

    public Supervisor(WorkerLauncher launcher) {
        this.launcher = launcher;
    }

    @Override
    public String start(String dataSourceId, RuntimeStartSpec spec) {
        running.computeIfAbsent(dataSourceId, id -> launchAndStart(spec));
        return RUNNING;
    }

    @Override
    public String stop(String dataSourceId) {
        RunningWorker worker = running.remove(dataSourceId);
        if (worker != null) {
            worker.shutdown();
        }
        return STOPPED;
    }

    @Override
    public String state(String dataSourceId) {
        return running.containsKey(dataSourceId) ? RUNNING : STOPPED;
    }

    @Override
    public long applyValues(String dataSourceId, List<NeutralValue> values) {
        RunningWorker worker = running.get(dataSourceId);
        if (worker == null) {
            throw new IllegalStateException("data source is not running: " + dataSourceId);
        }
        List<Value> protoValues = values.stream().map(Supervisor::toProto).toList();
        return worker.client().applyValues(protoValues);
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

    private RunningWorker launchAndStart(RuntimeStartSpec spec) {
        int controlPort = PortAllocator.freeLoopbackPort();
        LaunchedWorker launched;
        try {
            launched = launcher.launch(spec.protocol(), controlPort);
        } catch (Exception e) {
            throw new WorkerLaunchException("failed to launch " + spec.protocol() + " worker", e);
        }
        WorkerClient client = new WorkerClient("127.0.0.1", controlPort);
        try {
            awaitReady(client);
            client.configure(toProtoSchema(spec), spec.listenPort());
            client.start();
            return new RunningWorker(launched, client);
        } catch (RuntimeException e) {
            client.close();
            launched.close();
            throw e;
        }
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

    private record RunningWorker(LaunchedWorker launched, WorkerClient client) {

        void shutdown() {
            try {
                client.stop();
            } catch (RuntimeException ignored) {
                // best effort; we are tearing down
            }
            client.close();
            launched.close();
        }
    }
}
