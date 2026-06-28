package com.ainclusive.iotsim.worker.opcua;

import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import com.ainclusive.iotsim.workercontract.v1.Quality;
import com.ainclusive.iotsim.workercontract.v1.Value;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.subscriptions.ManagedSubscription;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.DateTime;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.StatusCode;

/**
 * OPC UA <em>client-mode</em> live capture (IS-045): connects to a real source,
 * subscribes to its schema variables, and forwards every observed value change as
 * protocol-neutral proto {@link Value}s to a sink until {@link #stop()}. Mirrors
 * {@link OpcUaDiscovery} (one-shot/client mode) and never touches the embedded
 * server runtime. See backend-specs/02 §6 and 01 §3.
 *
 * <p>"Every change, no sampling" per backend-specs/01: a subscription with a short
 * publishing interval reports each value change the server samples; the recording
 * path keeps the full fidelity (distinct from the conflated live-out path).
 */
final class OpcUaCapture {

    private static final double PUBLISHING_INTERVAL_MILLIS = 200.0;

    private final OpcUaClient client;
    private final ManagedSubscription subscription;

    private OpcUaCapture(OpcUaClient client, ManagedSubscription subscription) {
        this.client = client;
        this.subscription = subscription;
    }

    /** A variable to capture: its neutral node id (parseable OPC UA NodeId) and neutral type. */
    record NodeSpec(String nodeId, String dataType) {}

    /**
     * Connects, subscribes to the given variables, and starts streaming changes to
     * {@code sink} (one list per subscription publish). The caller owns the returned
     * handle and must {@link #stop()} it. Throws if the endpoint cannot be
     * reached/authenticated; per-node monitoring failures are surfaced by the server
     * as bad-quality values rather than failing the whole capture.
     */
    static OpcUaCapture start(String endpointUrl, String mode, String username, String secret,
            List<NodeSpec> nodes, Consumer<List<Value>> sink) throws Exception {
        OpcUaClient client = OpcUaClientSupport.connect(endpointUrl, mode, username, secret);
        try {
            Map<NodeId, NodeSpec> byNodeId = new HashMap<>();
            List<NodeId> nodeIds = new ArrayList<>();
            for (NodeSpec node : nodes) {
                NodeId id = NodeId.parse(node.nodeId());
                byNodeId.put(id, node);
                nodeIds.add(id);
            }
            ManagedSubscription subscription =
                    ManagedSubscription.create(client, PUBLISHING_INTERVAL_MILLIS);
            subscription.addDataChangeListener((items, values) -> {
                List<Value> batch = new ArrayList<>(items.size());
                for (int i = 0; i < items.size(); i++) {
                    NodeSpec spec = byNodeId.get(items.get(i).getNodeId());
                    if (spec != null) {
                        batch.add(toProtoValue(spec, values.get(i)));
                    }
                }
                if (!batch.isEmpty()) {
                    sink.accept(batch);
                }
            });
            if (!nodeIds.isEmpty()) {
                subscription.createDataItems(nodeIds);
            }
            return new OpcUaCapture(client, subscription);
        } catch (Exception e) {
            OpcUaClientSupport.disconnectQuietly(client);
            throw e;
        }
    }

    /** Cancels the subscription and disconnects; best-effort and idempotent. */
    void stop() {
        try {
            subscription.delete();
        } catch (Exception ignored) {
            // best effort; we are tearing down a capture session
        }
        OpcUaClientSupport.disconnectQuietly(client);
    }

    private static Value toProtoValue(NodeSpec spec, DataValue dv) {
        Object neutral = OpcUaTypes.fromOpcUaValue(spec.dataType(), dv.getValue().getValue());
        ValueCodec.Encoded enc = ValueCodec.encode(neutral);
        return Value.newBuilder()
                .setNodeId(spec.nodeId())
                .setSourceTimeMicros(sourceMicros(dv))
                .setValueEnc(ByteString.copyFrom(enc.bytes()))
                .setQuality(quality(dv.getStatusCode()))
                .build();
    }

    private static long sourceMicros(DataValue dv) {
        DateTime t = dv.getSourceTime();
        long millis = (t == null || t.getJavaTime() <= 0) ? System.currentTimeMillis() : t.getJavaTime();
        return millis * 1_000L;
    }

    private static Quality quality(StatusCode status) {
        if (status == null || status.isGood()) {
            return Quality.GOOD;
        }
        return status.isBad() ? Quality.BAD : Quality.UNCERTAIN;
    }
}
