package com.ainclusive.iotsim.worker.modbus;

import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import com.ainclusive.iotsim.workercontract.v1.Quality;
import com.ainclusive.iotsim.workercontract.v1.Value;
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Modbus <em>client-mode</em> live capture: connects to a real source and polls
 * its configured registers/coils at a fixed interval, forwarding every observed
 * value change (including the initial snapshot) as protocol-neutral proto
 * {@link Value}s to a sink until {@link #stop()}. Modbus has no native
 * push/subscribe mechanism, so this realizes the {@code worker-contract}
 * Capture requirement's "no sampling" via a bounded poll interval instead of a
 * server-pushed subscription — see
 * openspec/changes/is-059-worker-modbus/specs/worker-contract/spec.md.
 *
 * <p>Each node's physical Modbus address is taken from its {@code nodeId},
 * which must follow this worker's Scan-produced convention (e.g. {@code
 * "hr:1000"}) — see {@link ModbusDiscovery}. A node whose id does not parse is
 * skipped rather than failing the whole capture, mirroring OPC UA's "one
 * unreadable variable does not suppress other values".
 */
final class ModbusCapture {

    private static final Logger LOG = LoggerFactory.getLogger(ModbusCapture.class);
    static final int DEFAULT_POLL_INTERVAL_MS = 500;

    private final ModbusTCPMaster master;
    private final Thread pollThread;
    private volatile boolean running = true;

    /** A variable to capture: its neutral node id (Scan-encoded address) and neutral type. */
    record NodeSpec(String nodeId, String dataType) {}

    private ModbusCapture(ModbusTCPMaster master, int unitId, List<NodeSpec> nodes, int pollIntervalMs,
            Consumer<List<Value>> sink) {
        this.master = master;
        this.pollThread = new Thread(() -> pollLoop(unitId, nodes, pollIntervalMs, sink), "modbus-capture");
        this.pollThread.setDaemon(true);
    }

    static ModbusCapture start(String endpointUrl, List<NodeSpec> nodes, Consumer<List<Value>> sink)
            throws Exception {
        ModbusDiscovery.Endpoint endpoint = ModbusDiscovery.parseEndpoint(endpointUrl);
        ModbusTCPMaster master = new ModbusTCPMaster(endpoint.host(), endpoint.port(), 2000, false);
        master.connect();
        ModbusCapture capture = new ModbusCapture(master, endpoint.unitId(), nodes, DEFAULT_POLL_INTERVAL_MS, sink);
        capture.pollThread.start();
        return capture;
    }

    private void pollLoop(int unitId, List<NodeSpec> nodes, int pollIntervalMs, Consumer<List<Value>> sink) {
        Map<String, Object> last = new HashMap<>();
        boolean first = true;
        while (running) {
            List<Value> batch = new ArrayList<>();
            for (NodeSpec node : nodes) {
                try {
                    NodeAddress address = parseNodeAddress(node.nodeId());
                    Object neutral = readValue(master, unitId, address, node.dataType());
                    Object previous = last.get(node.nodeId());
                    if (first || !Objects.equals(previous, neutral)) {
                        last.put(node.nodeId(), neutral);
                        batch.add(toProtoValue(node.nodeId(), neutral));
                    }
                } catch (Exception e) {
                    LOG.debug("Modbus capture: skipping node {}", node.nodeId(), e);
                }
            }
            first = false;
            if (!batch.isEmpty()) {
                sink.accept(batch);
            }
            if (!running) {
                break;
            }
            try {
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /** Stops the poll loop and disconnects; best-effort and idempotent. */
    void stop() {
        running = false;
        pollThread.interrupt();
        try {
            pollThread.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        try {
            master.disconnect();
        } catch (RuntimeException ignored) {
            // best effort; we are tearing down a capture session
        }
    }

    record NodeAddress(String kind, int address) {}

    static NodeAddress parseNodeAddress(String nodeId) {
        int colon = nodeId.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("nodeId is not in Scan address form: " + nodeId);
        }
        String prefix = nodeId.substring(0, colon);
        int address = Integer.parseInt(nodeId.substring(colon + 1));
        String kind = switch (prefix) {
            case "co" -> "COIL";
            case "di" -> "DISCRETE_INPUT";
            case "hr" -> "HOLDING_REGISTER";
            case "ir" -> "INPUT_REGISTER";
            default -> throw new IllegalArgumentException("unknown Modbus address prefix: " + prefix);
        };
        return new NodeAddress(kind, address);
    }

    private static Object readValue(ModbusTCPMaster master, int unitId, NodeAddress address, String dataType)
            throws Exception {
        return switch (address.kind()) {
            case "COIL" -> master.readCoils(unitId, address.address(), 1).getBit(0);
            case "DISCRETE_INPUT" -> master.readInputDiscretes(unitId, address.address(), 1).getBit(0);
            case "HOLDING_REGISTER" -> {
                int span = ModbusTypes.registerSpan(dataType);
                int[] raw = toRawValues(master.readMultipleRegisters(unitId, address.address(), span));
                yield ModbusTypes.fromRegisters(dataType, raw);
            }
            case "INPUT_REGISTER" -> {
                int span = ModbusTypes.registerSpan(dataType);
                int[] raw = toRawValues(master.readInputRegisters(unitId, address.address(), span));
                yield ModbusTypes.fromRegisters(dataType, raw);
            }
            default -> throw new IllegalArgumentException("unknown Modbus register kind: " + address.kind());
        };
    }

    private static int[] toRawValues(com.ghgande.j2mod.modbus.procimg.InputRegister[] registers) {
        int[] raw = new int[registers.length];
        for (int i = 0; i < registers.length; i++) {
            raw[i] = registers[i].toUnsignedShort();
        }
        return raw;
    }

    private static Value toProtoValue(String nodeId, Object neutral) {
        ValueCodec.Encoded enc = ValueCodec.encode(neutral);
        return Value.newBuilder()
                .setNodeId(nodeId)
                .setSourceTimeMicros(System.currentTimeMillis() * 1_000L)
                .setValueEnc(ByteString.copyFrom(enc.bytes()))
                .setValueKind(enc.kind().name())
                .setQuality(Quality.GOOD)
                .build();
    }
}
