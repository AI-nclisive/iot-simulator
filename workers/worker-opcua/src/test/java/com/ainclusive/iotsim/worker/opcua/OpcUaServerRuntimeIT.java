package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.workercontract.v1.DataTypeMemberMsg;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.core.types.DynamicOptionSetType;
import org.eclipse.milo.opcua.sdk.core.types.DynamicStructType;
import org.eclipse.milo.opcua.sdk.core.types.DynamicUnionType;
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
    void clientReadsRuntimeMaterializedNativeStructureValue() throws Exception {
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
            runtime.updateValue("pump", runtime.structureValue(sourceTypeId, Map.of("running", true)));
            OpcUaClient client = OpcUaClient.create(runtime.endpointUrl());
            client.connect();
            try {
                DataValue value = client.readValue(0.0, TimestampsToReturn.Neither, runtime.variableNodeId("pump"));
                assertThat(value.getValue().getValue()).isInstanceOf(ExtensionObject.class);
                ExtensionObject extension = (ExtensionObject) value.getValue().getValue();
                assertThat(extension.getEncodingOrTypeId()).isEqualTo(runtime.localEncodingId(sourceTypeId));
                assertThat(extension.getBody()).isNotNull();
                assertThat(extension.decode(client.getDynamicEncodingContext()))
                        .isInstanceOf(DynamicStructType.class);
                DynamicStructType decoded = (DynamicStructType) extension.decode(client.getDynamicEncodingContext());
                assertThat(decoded.getMembers()).containsEntry("running", true);
            } finally {
                client.disconnect();
            }
        } finally {
            runtime.stop();
        }
    }

    @Test
    void clientReadsNestedRuntimeMaterializedNativeStructureValue() throws Exception {
        int port = freePort();
        String innerTypeId = "ns=4;s=InnerState";
        String outerTypeId = "ns=4;s=OuterState";
        NativeDataTypeDef inner = new NativeDataTypeDef(
                innerTypeId,
                "InnerState",
                List.of(DataTypeMemberMsg.newBuilder().setName("running").setDataType("BOOL")
                        .setValueRank("SCALAR").build()),
                List.of(), "ns=4;s=InnerState.DefaultBinary", "STRUCTURE");
        NativeDataTypeDef outer = new NativeDataTypeDef(
                outerTypeId,
                "OuterState",
                List.of(DataTypeMemberMsg.newBuilder().setName("state").setDataTypeNodeId(innerTypeId)
                        .setValueRank("SCALAR").build()),
                List.of(), "ns=4;s=OuterState.DefaultBinary", "STRUCTURE");
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                port, "127.0.0.1", "127.0.0.1",
                List.of(new VarDef("outer", null, "Outer", "VARIABLE", "", outerTypeId,
                        null, null, null, null, null)),
                List.of(inner, outer), AuthConfig.anonymous(), event -> { }, event -> { });
        runtime.start();
        try {
            runtime.updateValue("outer", runtime.structureValue(outerTypeId, Map.of(
                    "state", runtime.structureValue(innerTypeId, Map.of("running", true)))));
            OpcUaClient client = OpcUaClient.create(runtime.endpointUrl());
            client.connect();
            try {
                DataValue value = client.readValue(0.0, TimestampsToReturn.Neither, runtime.variableNodeId("outer"));
                DynamicStructType decoded = (DynamicStructType) ((ExtensionObject) value.getValue().getValue())
                        .decode(client.getDynamicEncodingContext());
                assertThat(decoded.getMembers().get("state")).isInstanceOf(DynamicStructType.class);
                DynamicStructType nested = (DynamicStructType) decoded.getMembers().get("state");
                assertThat(nested.getMembers()).containsEntry("running", true);
            } finally {
                client.disconnect();
            }
        } finally {
            runtime.stop();
        }
    }

    @Test
    void clientReadsArrayOfNestedRuntimeMaterializedStructures() throws Exception {
        int port = freePort();
        String innerTypeId = "ns=4;s=InnerState";
        String outerTypeId = "ns=4;s=OuterState";
        NativeDataTypeDef inner = new NativeDataTypeDef(
                innerTypeId,
                "InnerState",
                List.of(DataTypeMemberMsg.newBuilder().setName("running").setDataType("BOOL")
                        .setValueRank("SCALAR").build()),
                List.of(), "ns=4;s=InnerState.DefaultBinary", "STRUCTURE");
        NativeDataTypeDef outer = new NativeDataTypeDef(
                outerTypeId,
                "OuterState",
                List.of(DataTypeMemberMsg.newBuilder().setName("states").setDataTypeNodeId(innerTypeId)
                        .setValueRank("ARRAY").addArrayDimensions(2).build()),
                List.of(), "ns=4;s=OuterState.DefaultBinary", "STRUCTURE");
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                port, "127.0.0.1", "127.0.0.1",
                List.of(new VarDef("outer", null, "Outer", "VARIABLE", "", outerTypeId,
                        null, null, null, null, null)),
                List.of(inner, outer), AuthConfig.anonymous(), event -> { }, event -> { });
        runtime.start();
        try {
            DynamicStructType[] states = {
                    (DynamicStructType) runtime.structureValue(innerTypeId, Map.of("running", true)),
                    (DynamicStructType) runtime.structureValue(innerTypeId, Map.of("running", false))};
            runtime.updateValue("outer", runtime.structureValue(outerTypeId, Map.of("states", states)));
            OpcUaClient client = OpcUaClient.create(runtime.endpointUrl());
            client.connect();
            try {
                DataValue value = client.readValue(0.0, TimestampsToReturn.Neither, runtime.variableNodeId("outer"));
                DynamicStructType decoded = (DynamicStructType) ((ExtensionObject) value.getValue().getValue())
                        .decode(client.getDynamicEncodingContext());
                assertThat(decoded.getMembers().get("states")).isInstanceOf(DynamicStructType[].class);
                DynamicStructType[] decodedStates = (DynamicStructType[]) decoded.getMembers().get("states");
                assertThat(decodedStates).extracting(state -> state.getMembers().get("running"))
                        .containsExactly(true, false);
            } finally {
                client.disconnect();
            }
        } finally {
            runtime.stop();
        }
    }

    @Test
    void clientReadsRuntimeMaterializedUnionValue() throws Exception {
        int port = freePort();
        String unionTypeId = "ns=4;s=SwitchValue";
        NativeDataTypeDef union = new NativeDataTypeDef(
                unionTypeId,
                "SwitchValue",
                List.of(
                        DataTypeMemberMsg.newBuilder().setName("enabled").setDataType("BOOL")
                                .setValueRank("SCALAR").build(),
                        DataTypeMemberMsg.newBuilder().setName("count").setDataType("INT32")
                                .setValueRank("SCALAR").build()),
                List.of(), "ns=4;s=SwitchValue.DefaultBinary", "UNION");
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                port, "127.0.0.1", "127.0.0.1",
                List.of(new VarDef("switch", null, "Switch", "VARIABLE", "", unionTypeId,
                        null, null, null, null, null)),
                List.of(union), AuthConfig.anonymous(), event -> { }, event -> { });
        runtime.start();
        try {
            runtime.updateValue("switch", runtime.unionValue(unionTypeId, "count", 7));
            OpcUaClient client = OpcUaClient.create(runtime.endpointUrl());
            client.connect();
            try {
                DataValue value = client.readValue(0.0, TimestampsToReturn.Neither, runtime.variableNodeId("switch"));
                DynamicUnionType decoded = (DynamicUnionType) ((ExtensionObject) value.getValue().getValue())
                        .decode(client.getDynamicEncodingContext());
                assertThat(decoded.getValue()).hasValueSatisfying(selected -> {
                    assertThat(selected.fieldName()).isEqualTo("count");
                    assertThat(selected.fieldValue()).isEqualTo(7);
                });
            } finally {
                client.disconnect();
            }
        } finally {
            runtime.stop();
        }
    }

    @Test
    void clientReadsRuntimeMaterializedOptionSetValue() throws Exception {
        int port = freePort();
        String typeId = "ns=4;s=AccessFlags";
        NativeDataTypeDef optionSet = new NativeDataTypeDef(
                typeId,
                "AccessFlags",
                List.of(),
                List.of(
                        com.ainclusive.iotsim.workercontract.v1.DataTypeEnumValueMsg.newBuilder()
                                .setName("Read").setValue(0).build(),
                        com.ainclusive.iotsim.workercontract.v1.DataTypeEnumValueMsg.newBuilder()
                                .setName("Write").setValue(1).build()),
                null, "OPTION_SET");
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                port, "127.0.0.1", "127.0.0.1",
                List.of(new VarDef("flags", null, "Flags", "VARIABLE", "", typeId,
                        null, null, null, null, null)),
                List.of(optionSet), AuthConfig.anonymous(), event -> { }, event -> { });
        runtime.start();
        try {
            runtime.updateValue("flags", runtime.optionSetValue(typeId, new byte[] {3}, new byte[] {3}));
            OpcUaClient client = OpcUaClient.create(runtime.endpointUrl());
            client.connect();
            try {
                DataValue value = client.readValue(0.0, TimestampsToReturn.Neither, runtime.variableNodeId("flags"));
                DynamicOptionSetType decoded = (DynamicOptionSetType) ((ExtensionObject) value.getValue().getValue())
                        .decode(client.getDynamicEncodingContext());
                assertThat(decoded.getValue().bytes()).containsExactly(3);
                assertThat(decoded.getValidBits().bytes()).containsExactly(3);
                assertThat(decoded.getName(0)).contains("Read");
                assertThat(decoded.getName(1)).contains("Write");
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
