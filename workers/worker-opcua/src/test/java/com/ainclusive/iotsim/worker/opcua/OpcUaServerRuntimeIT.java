package com.ainclusive.iotsim.worker.opcua;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.junit.jupiter.api.Test;

/**
 * Brings up a real Milo OPC UA server from a schema and verifies a projected
 * value is readable by an OPC UA client over loopback. The core BE-R6 check.
 */
class OpcUaServerRuntimeIT {

    @Test
    void clientReadsProjectedVariableValue() throws Exception {
        int port = freePort();
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                port, List.of(new VarDef("temp", "Temperature", "FLOAT64")));
        runtime.start();
        try {
            OpcUaClient client = OpcUaClient.create(runtime.endpointUrl());
            client.connect().get(15, SECONDS);
            try {
                NodeId nodeId = runtime.variableNodeId("temp");

                DataValue initial = client.readValue(0.0, TimestampsToReturn.Both, nodeId).get(10, SECONDS);
                assertThat(((Number) initial.getValue().getValue()).doubleValue()).isEqualTo(0.0);

                runtime.updateValue("temp", 42.5);

                DataValue updated = client.readValue(0.0, TimestampsToReturn.Both, nodeId).get(10, SECONDS);
                assertThat(((Number) updated.getValue().getValue()).doubleValue()).isEqualTo(42.5);
            } finally {
                client.disconnect().get(10, SECONDS);
            }
        } finally {
            runtime.stop();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket()) {
            socket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            return socket.getLocalPort();
        }
    }
}
