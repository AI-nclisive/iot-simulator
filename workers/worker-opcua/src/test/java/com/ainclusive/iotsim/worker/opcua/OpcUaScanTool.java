package com.ainclusive.iotsim.worker.opcua;

import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manual debug utility (no {@code @Test} methods, never run by the test task):
 * connects to a real OPC UA server via this repo's own {@link OpcUaDiscovery}
 * and prints its address space (kind/dataType/path) plus a per-type node count.
 * Handy for diagnosing "wizard shows 0/wrong nodes" reports against a real
 * server without going through the full app. See the "Debug tools" section of
 * the top-level README for usage.
 */
final class OpcUaScanTool {

    private OpcUaScanTool() {}

    public static void main(String[] args) throws Exception {
        String endpoint = args.length > 0 ? args[0]
                : "opc.tcp://uademo.prosysopc.com:53530/OPCUA/SimulationServer";

        OpcUaDiscovery.Credentials anon = new OpcUaDiscovery.Credentials("ANONYMOUS", null, null);

        System.out.println("Testing connection to " + endpoint + " ...");
        OpcUaDiscovery.ConnectionTest test = OpcUaDiscovery.testConnection(endpoint, anon);
        System.out.println("  status=" + test.status() + " message=" + test.message());
        if (!OpcUaDiscovery.OK.equals(test.status())) {
            return;
        }

        System.out.println("Scanning " + endpoint + " ...");
        OpcUaDiscovery.ScanOutcome outcome = OpcUaDiscovery.scan(endpoint, anon, 5000, () -> System.out.println("connected"), soFar -> System.out.println("discovered " + soFar));
        System.out.println("  status=" + outcome.status() + " message=" + outcome.message());
        System.out.println("  truncated=" + outcome.truncated() + " unknownCount=" + outcome.unknownCount());
        System.out.println("  discovered " + outcome.nodes().size() + " nodes");
        System.out.println();

        List<SchemaNodeMsg> nodes = outcome.nodes();
        Map<String, List<SchemaNodeMsg>> byParent = new HashMap<>();
        for (SchemaNodeMsg n : nodes) {
            byParent.computeIfAbsent(n.getParentId(), k -> new ArrayList<>()).add(n);
        }

        System.out.println("Tree:");
        printChildren("", byParent, "");

        System.out.println();
        System.out.println("Distinct data types seen:");
        Map<String, Integer> typeCounts = new HashMap<>();
        for (SchemaNodeMsg n : nodes) {
            if ("VARIABLE".equals(n.getKind())) {
                String t = n.getDataType().isEmpty() ? "(unknown)" : n.getDataType();
                typeCounts.merge(t, 1, Integer::sum);
            }
        }
        typeCounts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> System.out.println("  " + e.getKey() + ": " + e.getValue()));
    }

    private static void printChildren(String parentId, Map<String, List<SchemaNodeMsg>> byParent, String indent) {
        List<SchemaNodeMsg> children = byParent.get(parentId);
        if (children == null) {
            return;
        }
        for (SchemaNodeMsg n : children) {
            String typeInfo = "VARIABLE".equals(n.getKind())
                    ? " : " + (n.getDataType().isEmpty() ? "unknown" : n.getDataType())
                    : "/";
            System.out.println(indent + n.getName() + typeInfo + "  [" + n.getNodeId() + "]");
            printChildren(n.getNodeId(), byParent, indent + "  ");
        }
    }
}
