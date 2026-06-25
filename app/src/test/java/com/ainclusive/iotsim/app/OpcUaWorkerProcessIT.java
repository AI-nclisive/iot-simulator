package com.ainclusive.iotsim.app;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import com.ainclusive.iotsim.supervisor.PortAllocator;
import com.ainclusive.iotsim.supervisor.ProcessWorkerLauncher;
import com.ainclusive.iotsim.supervisor.Supervisor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.junit.jupiter.api.Test;

/**
 * End-to-end spawn of the real packaged OPC UA worker (installDist) through the
 * supervisor, then a real Milo OPC UA client connects to the worker's endpoint,
 * reads a projected variable, and confirms an ApplyValues update crosses the
 * process boundary onto the live address space. The core IS-039 proof.
 * See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
class OpcUaWorkerProcessIT {

    private static final String DIST_PROPERTY = "iotsim.worker.opcua.dist";
    // Namespace URI the worker registers for schema-derived nodes (SchemaNamespace.URI).
    private static final String SCHEMA_NS_URI = "urn:iotsim:opcua";

    @Test
    void clientReadsValueProjectedThroughSpawnedWorker() throws Exception {
        List<String> command = workerCommandOrSkip();

        Supervisor supervisor = new Supervisor(new ProcessWorkerLauncher(Map.of("OPC_UA", command)));
        int listenPort = PortAllocator.freeLoopbackPort();
        RuntimeStartSpec spec = new RuntimeStartSpec("OPC_UA", 1, List.of(variable("temp", "Temperature")), listenPort);

        assertThat(supervisor.start("ds1", spec)).isEqualTo("RUNNING");
        try {
            OpcUaClient client = OpcUaClient.create("opc.tcp://127.0.0.1:" + listenPort + "/iotsim");
            try {
                client.connect().get(15, SECONDS);

                NodeId nodeId = new NodeId(schemaNamespaceIndex(client), "temp");

                DataValue initial = client.readValue(0.0, TimestampsToReturn.Both, nodeId).get(10, SECONDS);
                assertThat(initial.getStatusCode().isGood()).as("initial read status").isTrue();
                assertThat(((Number) initial.getValue().getValue()).doubleValue()).isEqualTo(0.0);

                supervisor.applyValues("ds1", List.of(NeutralValue.good("temp", Instant.now(), 42.5)));

                assertThat(awaitValue(client, nodeId)).isEqualTo(42.5);
            } finally {
                client.disconnect().get(10, SECONDS);
            }
        } finally {
            assertThat(supervisor.stop("ds1")).isEqualTo("STOPPED");
        }
    }

    private static SchemaNode variable(String id, String name) {
        return new SchemaNode(id, null, id, name, NodeKind.VARIABLE,
                DataType.FLOAT64, ValueRank.SCALAR, Access.READ_WRITE, null, null);
    }

    /** Resolves the schema namespace index from the server's NamespaceArray. */
    private static int schemaNamespaceIndex(OpcUaClient client) throws Exception {
        DataValue nsArray = client.readValue(0.0, TimestampsToReturn.Neither, Identifiers.Server_NamespaceArray)
                .get(10, SECONDS);
        String[] namespaces = (String[]) nsArray.getValue().getValue();
        int index = Arrays.asList(namespaces).indexOf(SCHEMA_NS_URI);
        assertThat(index).as("schema namespace %s registered", SCHEMA_NS_URI).isGreaterThanOrEqualTo(0);
        return index;
    }

    /** Re-reads until the projected value lands, up to ~5s, to absorb stream timing. */
    private static double awaitValue(OpcUaClient client, NodeId nodeId) throws Exception {
        double last = Double.NaN;
        for (int attempt = 0; attempt < 25; attempt++) {
            DataValue value = client.readValue(0.0, TimestampsToReturn.Both, nodeId).get(10, SECONDS);
            if (value.getValue().getValue() instanceof Number number) {
                last = number.doubleValue();
                if (last == 42.5) {
                    return last;
                }
            }
            Thread.sleep(200);
        }
        return last;
    }

    /** Resolves the installDist launcher script, or skips the test if it is absent. */
    private static List<String> workerCommandOrSkip() {
        String dist = System.getProperty(DIST_PROPERTY);
        assumeTrue(dist != null && !dist.isBlank(),
                "set -D" + DIST_PROPERTY + " to the worker-opcua installDist dir to run this IT");
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        Path bin = Path.of(dist, "bin", windows ? "worker-opcua.bat" : "worker-opcua");
        assumeTrue(Files.isRegularFile(bin), "worker launcher not found at " + bin);
        return List.of(bin.toString());
    }
}
