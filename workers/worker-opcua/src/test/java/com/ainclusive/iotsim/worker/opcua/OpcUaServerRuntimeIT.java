package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.workercontract.v1.DataTypeMemberMsg;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.QualifiedName;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.eclipse.milo.opcua.stack.core.types.structured.ReadValueId;
import org.eclipse.milo.opcua.stack.core.types.structured.StructureDefinition;
import org.junit.jupiter.api.Test;

/**
 * Brings up a real Milo OPC UA server from a schema and verifies a projected
 * value is readable by an OPC UA client over loopback. The core IS-038 check.
 */
class OpcUaServerRuntimeIT {

    @Test
    void clientReadsPublishedNativeStructureDefinition() throws Exception {
        int port = freePort();
        String sourceTypeId = "ns=4;s=PumpState";
        NativeDataTypeDef type = new NativeDataTypeDef(
                sourceTypeId,
                "PumpState",
                List.of(DataTypeMemberMsg.newBuilder().setName("running").setDataType("BOOL")
                        .setValueRank("SCALAR").build()),
                List.of(),
                "ns=4;s=PumpState.DefaultBinary",
                "STRUCTURE");
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                port, "127.0.0.1", "127.0.0.1",
                List.of(new VarDef("pump", null, "Pump", "VARIABLE", "", sourceTypeId,
                        null, null, null, null, null)),
                List.of(type), AuthConfig.anonymous(), event -> { }, event -> { });
        runtime.start();
        try {
            OpcUaClient client = OpcUaClient.create(runtime.endpointUrl());
            client.connect();
            try {
                DataValue value = client.read(0.0, TimestampsToReturn.Neither, List.of(new ReadValueId(
                        runtime.localDataTypeId(sourceTypeId),
                        org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint(23),
                        null,
                        QualifiedName.NULL_VALUE))).getResults()[0];
                assertThat(value.getValue().getValue()).isInstanceOf(ExtensionObject.class);
                StructureDefinition definition = (StructureDefinition) ((ExtensionObject) value.getValue().getValue())
                        .decode(client.getStaticEncodingContext());
                assertThat(definition.getFields()).extracting(field -> field.getName()).containsExactly("running");
                assertThat(definition.getDefaultEncodingId()).isEqualTo(runtime.localEncodingId(sourceTypeId));
            } finally {
                client.disconnect();
            }
        } finally {
            runtime.stop();
        }
    }

    @Test
    void clientReadsProjectedVariableValue() throws Exception {
        int port = freePort();
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                port, List.of(new VarDef("temp", "Temperature", "FLOAT64")));
        runtime.start();
        try {
            OpcUaClient client = OpcUaClient.create(runtime.endpointUrl());
            client.connect();
            try {
                NodeId nodeId = runtime.variableNodeId("temp");

                DataValue initial = client.readValue(0.0, TimestampsToReturn.Both, nodeId);
                assertThat(((Number) initial.getValue().getValue()).doubleValue()).isEqualTo(0.0);

                runtime.updateValue("temp", 42.5);

                DataValue updated = client.readValue(0.0, TimestampsToReturn.Both, nodeId);
                assertThat(((Number) updated.getValue().getValue()).doubleValue()).isEqualTo(42.5);
            } finally {
                client.disconnect();
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
