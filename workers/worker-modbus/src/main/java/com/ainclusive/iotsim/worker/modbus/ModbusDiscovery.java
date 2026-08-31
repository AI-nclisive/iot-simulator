package com.ainclusive.iotsim.worker.modbus;

import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import com.ghgande.j2mod.modbus.ModbusSlaveException;
import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

/**
 * Real-source discovery for Modbus TCP. Unlike OPC UA there is no
 * browsing/metadata to read, so {@code Scan} has the worker itself actively
 * probe a bounded set of candidate addresses — see
 * openspec/changes/is-059-worker-modbus/design.md decision 3.
 *
 * <p>The {@code ProtocolDataSource} contract's {@code endpoint_url} carries no
 * dedicated Modbus unit-id field; this worker accepts an optional
 * {@code #<unitId>} suffix (e.g. {@code "10.20.4.40:502#1"}), defaulting to
 * {@link #DEFAULT_UNIT_ID} when absent, rather than proposing a
 * worker-contract change for a single extra integer.
 *
 * <p>This class also owns the {@code nodeId} <-> physical-address convention
 * ({@code "hr:1000"}, {@code "hr32:1000"}, ...) that {@link ModbusCapture}
 * reads back via {@link #parseNodeAddress}, so the two directions of that
 * convention live in exactly one place.
 */
final class ModbusDiscovery {

    static final int DEFAULT_UNIT_ID = 1;

    private static final int DEFAULT_SCAN_RANGE = 64;
    private static final int CHUNK_SIZE = 16;
    /**
     * Stop probing one object type after this many consecutive misses. A chunk
     * failure only ever costs one extra single-address probe (never a
     * chunk-sized fallback), so the worst case per object type is bounded by
     * {@code MAX_CONSECUTIVE_FAILURES} single reads, never a sweep of the full
     * 64k address space.
     */
    private static final int MAX_CONSECUTIVE_FAILURES = 5;

    private ModbusDiscovery() {}

    record Endpoint(String host, int port, int unitId) {}

    static Endpoint parseEndpoint(String endpointUrl) {
        String withoutUnit = endpointUrl;
        int unitId = DEFAULT_UNIT_ID;
        int hash = endpointUrl.indexOf('#');
        if (hash >= 0) {
            withoutUnit = endpointUrl.substring(0, hash);
            unitId = Integer.parseInt(endpointUrl.substring(hash + 1));
        }
        int colon = withoutUnit.lastIndexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("Modbus endpoint must be host:port, got: " + endpointUrl);
        }
        String host = withoutUnit.substring(0, colon);
        int port = Integer.parseInt(withoutUnit.substring(colon + 1));
        return new Endpoint(host, port, unitId);
    }

    /** One resolved {@code nodeId}: its Modbus object type and base register/coil address. */
    record NodeAddress(ModbusTypes.ModbusRegisterKind kind, int address) {}

    /**
     * Parses a Scan-produced {@code nodeId} (e.g. {@code "hr:1000"}) back into
     * its physical address. The {@code 32} suffix Scan adds to an advisory
     * 32-bit-pairing node ({@code "hr32:1000"}, see {@link #addPairHeuristics})
     * resolves to the same object type and base address as the plain form —
     * once a user accepts the pairing and assigns it a 32-bit data type, it
     * reads/writes the same two registers the advisory node described.
     */
    static NodeAddress parseNodeAddress(String nodeId) {
        int colon = nodeId.indexOf(':');
        if (colon < 0) {
            throw new IllegalArgumentException("nodeId is not in Scan address form: " + nodeId);
        }
        String prefix = nodeId.substring(0, colon);
        int address = Integer.parseInt(nodeId.substring(colon + 1));
        String base = prefix.endsWith("32") ? prefix.substring(0, prefix.length() - 2) : prefix;
        ModbusTypes.ModbusRegisterKind kind = switch (base) {
            case "co" -> ModbusTypes.ModbusRegisterKind.COIL;
            case "di" -> ModbusTypes.ModbusRegisterKind.DISCRETE_INPUT;
            case "hr" -> ModbusTypes.ModbusRegisterKind.HOLDING_REGISTER;
            case "ir" -> ModbusTypes.ModbusRegisterKind.INPUT_REGISTER;
            default -> throw new IllegalArgumentException("unknown Modbus address prefix: " + prefix);
        };
        return new NodeAddress(kind, address);
    }

    record ConnectionTest(String status, String message) {}

    static ConnectionTest testConnection(String endpointUrl) {
        try {
            Endpoint endpoint = parseEndpoint(endpointUrl);
            ModbusTCPMaster master = connect(endpoint);
            try {
                return new ConnectionTest("OK", "");
            } finally {
                master.disconnect();
            }
        } catch (Exception e) {
            return new ConnectionTest("UNREACHABLE", e.getMessage());
        }
    }

    /** Connects a fresh master to the endpoint; shared by TestConnection/Scan/Capture. */
    static ModbusTCPMaster connect(Endpoint endpoint) throws Exception {
        ModbusTCPMaster master = new ModbusTCPMaster(endpoint.host(), endpoint.port(), 2000, false);
        master.connect();
        return master;
    }

    record ScanOutcome(List<SchemaNodeMsg> nodes, String status, boolean truncated, int unknownCount, String message) {}

    static ScanOutcome scan(String endpointUrl, int maxNodes, Runnable onConnected, IntConsumer onProgress) {
        Endpoint endpoint;
        try {
            endpoint = parseEndpoint(endpointUrl);
        } catch (IllegalArgumentException e) {
            return new ScanOutcome(List.of(), "UNREACHABLE", false, 0, e.getMessage());
        }
        ModbusTCPMaster master;
        try {
            master = connect(endpoint);
        } catch (Exception e) {
            return new ScanOutcome(List.of(), "UNREACHABLE", false, 0, e.getMessage());
        }
        onConnected.run();
        try {
            int limit = maxNodes > 0 ? maxNodes : DEFAULT_SCAN_RANGE * 4;
            List<SchemaNodeMsg> nodes = new ArrayList<>();
            List<String> errors = new ArrayList<>();
            probe(nodes, limit, onProgress, errors, new BitObjectProbe(master, endpoint.unitId(), "co", true));
            probe(nodes, limit, onProgress, errors, new BitObjectProbe(master, endpoint.unitId(), "di", false));
            List<Integer> holdingAddresses = new ArrayList<>();
            probe(nodes, limit, onProgress, errors,
                    new RegisterObjectProbe(master, endpoint.unitId(), "hr", true, holdingAddresses));
            List<Integer> inputAddresses = new ArrayList<>();
            probe(nodes, limit, onProgress, errors,
                    new RegisterObjectProbe(master, endpoint.unitId(), "ir", false, inputAddresses));
            int unknownCount = addPairHeuristics(nodes, "hr", holdingAddresses) + addPairHeuristics(nodes, "ir", inputAddresses);
            boolean truncated = !errors.isEmpty() || nodes.size() >= limit;
            return new ScanOutcome(nodes, truncated ? "PARTIAL" : "OK", truncated, unknownCount, String.join("; ", errors));
        } finally {
            master.disconnect();
        }
    }

    /** One Modbus object type's read operations, abstracted so {@link #probe} has a single bounded algorithm. */
    private interface ObjectProbe {
        /** Reads {@code count} addresses starting at {@code address}; throws on any Modbus error. */
        void readChunk(int address, int count) throws Exception;

        /** Records address {@code address} as present (the read already succeeded). */
        void onPresent(List<SchemaNodeMsg> nodes, int address);
    }

    private record BitObjectProbe(ModbusTCPMaster master, int unitId, String prefix, boolean writable)
            implements ObjectProbe {
        @Override
        public void readChunk(int address, int count) throws Exception {
            if (writable) {
                master.readCoils(unitId, address, count);
            } else {
                master.readInputDiscretes(unitId, address, count);
            }
        }

        @Override
        public void onPresent(List<SchemaNodeMsg> nodes, int address) {
            ModbusTypes.ModbusRegisterKind kind =
                    writable ? ModbusTypes.ModbusRegisterKind.COIL : ModbusTypes.ModbusRegisterKind.DISCRETE_INPUT;
            String dataType = ModbusTypes.neutralTypeOf(kind, false);
            nodes.add(node(prefix + ":" + address, address, kind, dataType, writable ? "READ_WRITE" : "READ"));
        }
    }

    private record RegisterObjectProbe(ModbusTCPMaster master, int unitId, String prefix,
            boolean writable, List<Integer> discovered) implements ObjectProbe {
        @Override
        public void readChunk(int address, int count) throws Exception {
            if (writable) {
                master.readMultipleRegisters(unitId, address, count);
            } else {
                master.readInputRegisters(unitId, address, count);
            }
        }

        @Override
        public void onPresent(List<SchemaNodeMsg> nodes, int address) {
            ModbusTypes.ModbusRegisterKind kind = writable
                    ? ModbusTypes.ModbusRegisterKind.HOLDING_REGISTER : ModbusTypes.ModbusRegisterKind.INPUT_REGISTER;
            String dataType = ModbusTypes.neutralTypeOf(kind, false);
            nodes.add(node(prefix + ":" + address, address, kind, dataType, writable ? "READ_WRITE" : "READ"));
            discovered.add(address);
        }
    }

    /**
     * Bounded active probe shared by every Modbus object type: reads ahead in
     * chunks while addresses are present, and on any chunk failure falls back
     * to exactly one single-address read (never a chunk-sized fan-out) before
     * either resuming chunked reads (address present) or counting a miss
     * (address absent) — see {@link #MAX_CONSECUTIVE_FAILURES}. A transport-level
     * failure (not a Modbus protocol exception, e.g. a dropped connection) is
     * recorded in {@code errors} rather than silently treated as a normal
     * address-space miss, and stops this object type's probe early.
     */
    private static void probe(List<SchemaNodeMsg> nodes, int limit, IntConsumer onProgress, List<String> errors,
            ObjectProbe object) {
        int address = 0;
        int consecutiveFailures = 0;
        while (address < 65536 && nodes.size() < limit && consecutiveFailures < MAX_CONSECUTIVE_FAILURES) {
            int chunk = Math.min(CHUNK_SIZE, limit - nodes.size());
            try {
                object.readChunk(address, chunk);
                for (int i = 0; i < chunk; i++) {
                    object.onPresent(nodes, address + i);
                }
                address += chunk;
                consecutiveFailures = 0;
                onProgress.accept(nodes.size());
            } catch (ModbusSlaveException e) {
                if (chunk == 1) {
                    consecutiveFailures++;
                    address++;
                    continue;
                }
                try {
                    object.readChunk(address, 1);
                    object.onPresent(nodes, address);
                    address++;
                    consecutiveFailures = 0;
                } catch (Exception single) {
                    consecutiveFailures++;
                    address++;
                }
                onProgress.accept(nodes.size());
            } catch (Exception e) {
                errors.add(e.getMessage());
                return;
            }
        }
    }

    /**
     * For every adjacent pair of discovered single registers, adds an advisory
     * node representing a possible 32-bit reinterpretation, left with a blank
     * {@code data_type} so it is treated as unresolved/needs-confirmation by
     * the existing "unknown type blocks create" mechanism (protocol-model §3).
     * Its {@code nodeId} resolves back to the same address via
     * {@link #parseNodeAddress} once accepted.
     */
    private static int addPairHeuristics(List<SchemaNodeMsg> nodes, String prefix, List<Integer> addresses) {
        int count = 0;
        for (int i = 0; i + 1 < addresses.size(); i++) {
            int a = addresses.get(i);
            int b = addresses.get(i + 1);
            if (b == a + 1) {
                nodes.add(SchemaNodeMsg.newBuilder()
                        .setNodeId(prefix + "32:" + a)
                        .setPath(prefix + "32_" + a)
                        .setName(prefix + "32_" + a)
                        .setKind("VARIABLE")
                        .setAccess("hr".equals(prefix) ? "READ_WRITE" : "READ")
                        .setDescription("Possible 32-bit pairing of " + prefix + ":" + a + "/" + prefix + ":" + b
                                + " — confirm data type before use.")
                        .build());
                count++;
            }
        }
        return count;
    }

    private static SchemaNodeMsg node(String nodeId, int address, ModbusTypes.ModbusRegisterKind kind,
            String dataType, String access) {
        return SchemaNodeMsg.newBuilder()
                .setNodeId(nodeId)
                .setPath(nodeId.replace(':', '_'))
                .setName(nodeId)
                .setKind("VARIABLE")
                .setDataType(dataType)
                .setAccess(access)
                .setDescription(kind + " @ " + address)
                .build();
    }
}
