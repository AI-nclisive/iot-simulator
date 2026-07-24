package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link OpcUaDiscovery} as an OPC UA client against a real embedded Milo
 * server (the same projection used by the runtime) as a stand-in for a real
 * source. The core IS-043 check: scan browses the address space into neutral
 * schema nodes with mapped types, honours the node cap, and classifies failures.
 */
class OpcUaDiscoveryIT {

    private static final OpcUaDiscovery.Credentials ANON =
            new OpcUaDiscovery.Credentials("ANONYMOUS", null, null);

    @Test
    void scanDiscoversVariablesWithMappedTypes() throws Exception {
        int port = freePort();
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(port, List.of(
                new VarDef("temp", "Temperature", "FLOAT64"),
                new VarDef("count", "Count", "INT32"),
                new VarDef("flag", "Flag", "BOOL"),
                new VarDef("label", "Label", "STRING"),
                new VarDef("note", "Note", "LOCALIZED_TEXT"),
                new VarDef("small", "Small", "INT8"),
                new VarDef("usmall", "USmall", "UINT8"),
                new VarDef("id", "Id", "GUID"),
                new VarDef("quality", "Quality", "STATUS_CODE"),
                new VarDef("qname", "QName", "QUALIFIED_NAME"),
                new VarDef("target", "Target", "NODE_ID"),
                new VarDef("xtarget", "XTarget", "EXPANDED_NODE_ID"),
                new VarDef("xml", "Xml", "XML_ELEMENT")));
        runtime.start();
        try {
            OpcUaDiscovery.ScanOutcome outcome = OpcUaDiscovery.scan(runtime.endpointUrl(), ANON, 0, () -> { }, soFar -> { });

            assertThat(outcome.status()).isEqualTo(OpcUaDiscovery.OK);
            assertThat(outcome.truncated()).isFalse();
            assertThat(outcome.unknownCount()).isZero();
            Map<String, SchemaNodeMsg> byName = outcome.nodes().stream()
                    .collect(Collectors.toMap(SchemaNodeMsg::getName, Function.identity()));
            assertThat(byName.keySet()).contains("Temperature", "Count", "Flag", "Label", "Note",
                    "Small", "USmall", "Id", "Quality", "QName", "Target", "XTarget", "Xml");
            assertThat(byName.get("Temperature").getKind()).isEqualTo("VARIABLE");
            assertThat(byName.get("Temperature").getDataType()).isEqualTo("FLOAT64");
            assertThat(byName.get("Count").getDataType()).isEqualTo("INT32");
            assertThat(byName.get("Flag").getDataType()).isEqualTo("BOOL");
            assertThat(byName.get("Label").getDataType()).isEqualTo("STRING");
            assertThat(byName.get("Note").getDataType()).isEqualTo("LOCALIZED_TEXT");
            assertThat(byName.get("Small").getDataType()).isEqualTo("INT8");
            assertThat(byName.get("USmall").getDataType()).isEqualTo("UINT8");
            assertThat(byName.get("Id").getDataType()).isEqualTo("GUID");
            assertThat(byName.get("Quality").getDataType()).isEqualTo("STATUS_CODE");
            assertThat(byName.get("QName").getDataType()).isEqualTo("QUALIFIED_NAME");
            assertThat(byName.get("Target").getDataType()).isEqualTo("NODE_ID");
            assertThat(byName.get("XTarget").getDataType()).isEqualTo("EXPANDED_NODE_ID");
            assertThat(byName.get("Xml").getDataType()).isEqualTo("XML_ELEMENT");
            assertThat(byName.get("Temperature").getAccess()).isEqualTo("READ");
            assertThat(byName.get("Temperature").getValueRank()).isEqualTo("SCALAR");
        } finally {
            runtime.stop();
        }
    }

    @Test
    void scanStopsAtNodeCapAndReportsPartial() throws Exception {
        int port = freePort();
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(port, List.of(
                new VarDef("a", "A", "INT32"),
                new VarDef("b", "B", "INT32"),
                new VarDef("c", "C", "INT32"),
                new VarDef("d", "D", "INT32")));
        runtime.start();
        try {
            OpcUaDiscovery.ScanOutcome outcome = OpcUaDiscovery.scan(runtime.endpointUrl(), ANON, 2, () -> { }, soFar -> { });

            assertThat(outcome.status()).isEqualTo(OpcUaDiscovery.PARTIAL);
            assertThat(outcome.truncated()).isTrue();
            assertThat(outcome.nodes()).hasSize(2);
        } finally {
            runtime.stop();
        }
    }

    @Test
    void scanPreservesNestedFolderAndVariableHierarchy() throws Exception {
        int port = freePort();
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(port, List.of(
                new VarDef("plant", null, "Plant", "FOLDER", null),
                new VarDef("tank", "plant", "Tank1", "FOLDER", null),
                new VarDef("temperature", "tank", "Temperature", "VARIABLE", "FLOAT64")));
        runtime.start();
        try {
            OpcUaDiscovery.ScanOutcome outcome = OpcUaDiscovery.scan(
                    runtime.endpointUrl(), ANON, 0, () -> { }, soFar -> { });

            assertThat(outcome.status()).isEqualTo(OpcUaDiscovery.OK);
            Map<String, SchemaNodeMsg> byName = outcome.nodes().stream()
                    .collect(Collectors.toMap(SchemaNodeMsg::getName, Function.identity()));
            assertThat(byName.get("Plant").getKind()).isEqualTo("FOLDER");
            assertThat(byName.get("Tank1").getParentId()).isEqualTo(byName.get("Plant").getNodeId());
            assertThat(byName.get("Temperature").getParentId()).isEqualTo(byName.get("Tank1").getNodeId());
            assertThat(byName.get("Temperature").getDataType()).isEqualTo("FLOAT64");
        } finally {
            runtime.stop();
        }
    }

    @Test
    void scanPreservesImportedObjectHierarchy() throws Exception {
        int port = freePort();
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(port, List.of(
                new VarDef("plant", null, "Plant", "OBJECT", null),
                new VarDef("temperature", "plant", "Temperature", "VARIABLE", "FLOAT64")));
        runtime.start();
        try {
            OpcUaDiscovery.ScanOutcome outcome = OpcUaDiscovery.scan(
                    runtime.endpointUrl(), ANON, 0, () -> { }, soFar -> { });

            Map<String, SchemaNodeMsg> byName = outcome.nodes().stream()
                    .collect(Collectors.toMap(SchemaNodeMsg::getName, Function.identity()));
            assertThat(byName.get("Temperature").getParentId()).isEqualTo(byName.get("Plant").getNodeId());
        } finally {
            runtime.stop();
        }
    }

    @Test
    void scanIncludesPropertyOfVariableChildren() throws Exception {
        // IS-189: A real device commonly exposes Properties on a Variable (EURange,
        // EngineeringUnits, Quality, ...) — a HasProperty child whose parent is the
        // owning Variable itself. The neutral schema now supports VARIABLE as a parent
        // for HasProperty/HasComponent children, so these are included with proper
        // reference type capture.
        int port = freePort();
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(port, List.of(
                new VarDef("temperature", null, "Temperature", "VARIABLE", "FLOAT64"),
                new VarDef("quality", "temperature", "Quality", "VARIABLE", "STATUS_CODE", "HAS_PROPERTY")));
        runtime.start();
        try {
            OpcUaDiscovery.ScanOutcome outcome = OpcUaDiscovery.scan(
                    runtime.endpointUrl(), ANON, 0, () -> { }, soFar -> { });

            assertThat(outcome.status()).isEqualTo(OpcUaDiscovery.OK);
            Map<String, SchemaNodeMsg> byName = outcome.nodes().stream()
                    .collect(Collectors.toMap(SchemaNodeMsg::getName, Function.identity()));
            assertThat(byName).containsKey("Temperature");
            assertThat(byName).containsKey("Quality");
            SchemaNodeMsg quality = byName.get("Quality");
            assertThat(quality.getParentId()).isEqualTo(byName.get("Temperature").getNodeId());
        } finally {
            runtime.stop();
        }
    }

    @Test
    void testConnectionSucceedsAgainstRunningServer() throws Exception {
        int port = freePort();
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                port, List.of(new VarDef("temp", "Temperature", "FLOAT64")));
        runtime.start();
        try {
            OpcUaDiscovery.ConnectionTest result =
                    OpcUaDiscovery.testConnection(runtime.endpointUrl(), ANON);
            assertThat(result.status()).isEqualTo(OpcUaDiscovery.OK);
        } finally {
            runtime.stop();
        }
    }

    @Test
    void testConnectionReportsUnreachableWhenNothingListens() throws Exception {
        int port = freePort(); // nothing is bound here
        OpcUaDiscovery.ConnectionTest result = OpcUaDiscovery.testConnection(
                "opc.tcp://127.0.0.1:" + port + "/iotsim", ANON);
        assertThat(result.status()).isEqualTo(OpcUaDiscovery.UNREACHABLE);
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            return socket.getLocalPort();
        }
    }
}
