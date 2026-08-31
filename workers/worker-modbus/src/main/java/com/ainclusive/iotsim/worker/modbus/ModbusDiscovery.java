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
 * unit 1 when absent, rather than proposing a worker-contract change for a
 * single extra integer.
 */
final class ModbusDiscovery {

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
        int unitId = 1;
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

    record ConnectionTest(String status, String message) {}

    static ConnectionTest testConnection(String endpointUrl) {
        try {
            Endpoint endpoint = parseEndpoint(endpointUrl);
            ModbusTCPMaster master = new ModbusTCPMaster(endpoint.host(), endpoint.port(), 2000, false);
            try {
                master.connect();
                return new ConnectionTest("OK", "");
            } finally {
                master.disconnect();
            }
        } catch (Exception e) {
            return new ConnectionTest("UNREACHABLE", e.getMessage());
        }
    }

    record ScanOutcome(List<SchemaNodeMsg> nodes, String status, boolean truncated, int unknownCount, String message) {}

    static ScanOutcome scan(String endpointUrl, int maxNodes, Runnable onConnected, IntConsumer onProgress) {
        Endpoint endpoint;
        try {
            endpoint = parseEndpoint(endpointUrl);
        } catch (IllegalArgumentException e) {
            return new ScanOutcome(List.of(), "UNREACHABLE", false, 0, e.getMessage());
        }
        int limit = maxNodes > 0 ? maxNodes : DEFAULT_SCAN_RANGE * 4;
        ModbusTCPMaster master = new ModbusTCPMaster(endpoint.host(), endpoint.port(), 2000, false);
        try {
            master.connect();
        } catch (Exception e) {
            return new ScanOutcome(List.of(), "UNREACHABLE", false, 0, e.getMessage());
        }
        onConnected.run();
        try {
            List<SchemaNodeMsg> nodes = new ArrayList<>();
            boolean truncated = false;
            truncated |= probe(nodes, limit, onProgress, new BitObjectProbe(master, endpoint.unitId(), "co", "COIL", true));
            truncated |= probe(nodes, limit, onProgress, new BitObjectProbe(master, endpoint.unitId(), "di", "DISCRETE_INPUT", false));
            List<Integer> holdingAddresses = new ArrayList<>();
            truncated |= probe(nodes, limit, onProgress,
                    new RegisterObjectProbe(master, endpoint.unitId(), "hr", "HOLDING_REGISTER", true, holdingAddresses));
            List<Integer> inputAddresses = new ArrayList<>();
            truncated |= probe(nodes, limit, onProgress,
                    new RegisterObjectProbe(master, endpoint.unitId(), "ir", "INPUT_REGISTER", false, inputAddresses));
            int unknownCount = addPairHeuristics(nodes, "hr", holdingAddresses) + addPairHeuristics(nodes, "ir", inputAddresses);
            return new ScanOutcome(nodes, truncated ? "PARTIAL" : "OK", truncated, unknownCount, "");
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

    private record BitObjectProbe(ModbusTCPMaster master, int unitId, String prefix, String kind, boolean writable)
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
            nodes.add(node(prefix + ":" + address, address, kind, "BOOL", writable ? "READ_WRITE" : "READ"));
        }
    }

    private record RegisterObjectProbe(ModbusTCPMaster master, int unitId, String prefix, String kind,
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
            nodes.add(node(prefix + ":" + address, address, kind, "UINT16", writable ? "READ_WRITE" : "READ"));
            discovered.add(address);
        }
    }

    /**
     * Bounded active probe shared by every Modbus object type: reads ahead in
     * chunks while addresses are present, and on any chunk failure falls back
     * to exactly one single-address read (never a chunk-sized fan-out) before
     * either resuming chunked reads (address present) or counting a miss
     * (address absent) — see {@link #MAX_CONSECUTIVE_FAILURES}.
     */
    private static boolean probe(List<SchemaNodeMsg> nodes, int limit, IntConsumer onProgress, ObjectProbe object) {
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
                return true;
            }
        }
        return nodes.size() >= limit;
    }

    /**
     * For every adjacent pair of discovered single registers, adds an advisory
     * node representing a possible 32-bit reinterpretation, left with a blank
     * {@code data_type} so it is treated as unresolved/needs-confirmation by
     * the existing "unknown type blocks create" mechanism (protocol-model §3).
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

    private static SchemaNodeMsg node(String nodeId, int address, String registerKind, String dataType, String access) {
        return SchemaNodeMsg.newBuilder()
                .setNodeId(nodeId)
                .setPath(nodeId.replace(':', '_'))
                .setName(nodeId)
                .setKind("VARIABLE")
                .setDataType(dataType)
                .setAccess(access)
                .setDescription(registerKind + " @ " + address)
                .build();
    }
}
