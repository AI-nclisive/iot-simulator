package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import com.ainclusive.iotsim.workercontract.v1.Ack;
import com.ainclusive.iotsim.workercontract.v1.ConfigureRequest;
import com.ainclusive.iotsim.workercontract.v1.DataTypeEnumValueMsg;
import com.ainclusive.iotsim.workercontract.v1.DataTypeMemberMsg;
import com.ainclusive.iotsim.workercontract.v1.ProtocolDataSourceGrpc;
import com.ainclusive.iotsim.workercontract.v1.Schema;
import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import com.ainclusive.iotsim.workercontract.v1.StartRequest;
import com.ainclusive.iotsim.workercontract.v1.Value;
import com.ainclusive.iotsim.workercontract.v1.ValueBatch;
import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;
import java.net.ServerSocket;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.core.types.DynamicOptionSetType;
import org.eclipse.milo.opcua.sdk.core.types.DynamicStructType;
import org.eclipse.milo.opcua.sdk.core.types.DynamicUnionType;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.eclipse.milo.opcua.stack.core.types.enumerated.TimestampsToReturn;
import org.junit.jupiter.api.Test;

class NativeTreeReplayIT {

    @Test
    void appliesCanonicalTreeToPublishedNativeStructure() throws Exception {
        OpcUaProtocolService service = new OpcUaProtocolService();
        WorkerServer worker = new WorkerServer(0, service).start();
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", worker.port())
                .usePlaintext().build();
        try {
            ProtocolDataSourceGrpc.ProtocolDataSourceBlockingStub blocking =
                    ProtocolDataSourceGrpc.newBlockingStub(channel);
            String innerTypeId = "ns=4;s=PumpState";
            String typeId = "ns=4;s=PumpStates";
            blocking.configure(ConfigureRequest.newBuilder().setListenPort(freePort()).setSchema(Schema.newBuilder()
                    .addNodes(SchemaNodeMsg.newBuilder().setNodeId(innerTypeId).setKind("DATA_TYPE")
                            .setName("PumpState").setNativeTypeKind("STRUCTURE")
                            .setDataTypeDefaultEncodingId("ns=4;s=PumpState.DefaultBinary")
                            .addDataTypeMembers(DataTypeMemberMsg.newBuilder().setName("running")
                                    .setDataType("BOOL").setValueRank("SCALAR")))
                    .addNodes(SchemaNodeMsg.newBuilder().setNodeId(typeId).setKind("DATA_TYPE")
                            .setName("PumpStates").setNativeTypeKind("STRUCTURE")
                            .setDataTypeDefaultEncodingId("ns=4;s=PumpStates.DefaultBinary")
                            .addDataTypeMembers(DataTypeMemberMsg.newBuilder().setName("states")
                                    .setDataTypeNodeId(innerTypeId).setValueRank("ARRAY")))
                    .addNodes(SchemaNodeMsg.newBuilder().setNodeId("pump").setKind("VARIABLE")
                            .setName("Pump").setDataTypeNodeId(typeId))
                    .build()).build());
            assertThat(blocking.start(StartRequest.getDefaultInstance()).getOk()).isTrue();

            ValueCodec.Encoded tree = ValueCodec.encode(Map.of("states", List.of(
                    Map.of("running", true), Map.of("running", false))));
            CountDownLatch completed = new CountDownLatch(1);
            StreamObserver<ValueBatch> values = ProtocolDataSourceGrpc.newStub(channel).applyValues(new StreamObserver<>() {
                @Override public void onNext(Ack ignored) {}
                @Override public void onError(Throwable ignored) { completed.countDown(); }
                @Override public void onCompleted() { completed.countDown(); }
            });
            values.onNext(ValueBatch.newBuilder().addValues(Value.newBuilder().setNodeId("pump")
                    .setValueKind(tree.kind().name()).setValueEnc(ByteString.copyFrom(tree.bytes()))).build());
            values.onCompleted();
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();

            OpcUaClient client = OpcUaClient.create(service.opcUaEndpointUrl());
            client.connect();
            try {
                DataValue read = client.readValue(0.0, TimestampsToReturn.Neither, NodeId.parse("ns=2;s=pump"));
                ExtensionObject extension = (ExtensionObject) read.getValue().getValue();
                DynamicStructType decoded = (DynamicStructType) extension.decode(client.getDynamicEncodingContext());
                assertThat(decoded.getMembers().get("states")).isInstanceOf(DynamicStructType[].class);
                DynamicStructType[] states = (DynamicStructType[]) decoded.getMembers().get("states");
                assertThat(states).extracting(state -> state.getMembers().get("running"))
                        .containsExactly(true, false);
            } finally {
                client.disconnect();
            }
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            worker.stop();
        }
    }

    @Test
    void appliesCanonicalTreeToPublishedOptionSet() throws Exception {
        OpcUaProtocolService service = new OpcUaProtocolService();
        WorkerServer worker = new WorkerServer(0, service).start();
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", worker.port())
                .usePlaintext().build();
        try {
            ProtocolDataSourceGrpc.ProtocolDataSourceBlockingStub blocking =
                    ProtocolDataSourceGrpc.newBlockingStub(channel);
            String typeId = "ns=4;s=AccessFlags";
            blocking.configure(ConfigureRequest.newBuilder().setListenPort(freePort()).setSchema(Schema.newBuilder()
                    .addNodes(SchemaNodeMsg.newBuilder().setNodeId(typeId).setKind("DATA_TYPE")
                            .setName("AccessFlags").setNativeTypeKind("OPTION_SET")
                            .addAllDataTypeEnumValues(List.of(
                                    DataTypeEnumValueMsg.newBuilder().setName("Read").setValue(0).build(),
                                    DataTypeEnumValueMsg.newBuilder().setName("Write").setValue(1).build())))
                    .addNodes(SchemaNodeMsg.newBuilder().setNodeId("flags").setKind("VARIABLE")
                            .setName("Flags").setDataTypeNodeId(typeId))
                    .build()).build());
            assertThat(blocking.start(StartRequest.getDefaultInstance()).getOk()).isTrue();

            ValueCodec.Encoded tree = ValueCodec.encode(Map.of("value", new byte[] {3}, "validBits", new byte[] {3}));
            CountDownLatch completed = new CountDownLatch(1);
            StreamObserver<ValueBatch> values = ProtocolDataSourceGrpc.newStub(channel).applyValues(new StreamObserver<>() {
                @Override public void onNext(Ack ignored) {}
                @Override public void onError(Throwable ignored) { completed.countDown(); }
                @Override public void onCompleted() { completed.countDown(); }
            });
            values.onNext(ValueBatch.newBuilder().addValues(Value.newBuilder().setNodeId("flags")
                    .setValueKind(tree.kind().name()).setValueEnc(ByteString.copyFrom(tree.bytes()))).build());
            values.onCompleted();
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();

            OpcUaClient client = OpcUaClient.create(service.opcUaEndpointUrl());
            client.connect();
            try {
                DataValue read = client.readValue(0.0, TimestampsToReturn.Neither, NodeId.parse("ns=2;s=flags"));
                DynamicOptionSetType decoded = (DynamicOptionSetType) ((ExtensionObject) read.getValue().getValue())
                        .decode(client.getDynamicEncodingContext());
                assertThat(decoded.getValue().bytes()).containsExactly(3);
                assertThat(decoded.getValidBits().bytes()).containsExactly(3);
            } finally {
                client.disconnect();
            }
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            worker.stop();
        }
    }

    @Test
    void appliesCanonicalTreeToPublishedUnion() throws Exception {
        OpcUaProtocolService service = new OpcUaProtocolService();
        WorkerServer worker = new WorkerServer(0, service).start();
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", worker.port())
                .usePlaintext().build();
        try {
            ProtocolDataSourceGrpc.ProtocolDataSourceBlockingStub blocking =
                    ProtocolDataSourceGrpc.newBlockingStub(channel);
            String typeId = "ns=4;s=SwitchValue";
            blocking.configure(ConfigureRequest.newBuilder().setListenPort(freePort()).setSchema(Schema.newBuilder()
                    .addNodes(SchemaNodeMsg.newBuilder().setNodeId(typeId).setKind("DATA_TYPE")
                            .setName("SwitchValue").setNativeTypeKind("UNION")
                            .setDataTypeDefaultEncodingId("ns=4;s=SwitchValue.DefaultBinary")
                            .addDataTypeMembers(DataTypeMemberMsg.newBuilder().setName("enabled")
                                    .setDataType("BOOL").setValueRank("SCALAR"))
                            .addDataTypeMembers(DataTypeMemberMsg.newBuilder().setName("count")
                                    .setDataType("INT32").setValueRank("SCALAR")))
                    .addNodes(SchemaNodeMsg.newBuilder().setNodeId("switch").setKind("VARIABLE")
                            .setName("Switch").setDataTypeNodeId(typeId))
                    .build()).build());
            assertThat(blocking.start(StartRequest.getDefaultInstance()).getOk()).isTrue();

            ValueCodec.Encoded tree = ValueCodec.encode(Map.of("count", 7L));
            CountDownLatch completed = new CountDownLatch(1);
            StreamObserver<ValueBatch> values = ProtocolDataSourceGrpc.newStub(channel).applyValues(new StreamObserver<>() {
                @Override public void onNext(Ack ignored) {}
                @Override public void onError(Throwable ignored) { completed.countDown(); }
                @Override public void onCompleted() { completed.countDown(); }
            });
            values.onNext(ValueBatch.newBuilder().addValues(Value.newBuilder().setNodeId("switch")
                    .setValueKind(tree.kind().name()).setValueEnc(ByteString.copyFrom(tree.bytes()))).build());
            values.onCompleted();
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();

            OpcUaClient client = OpcUaClient.create(service.opcUaEndpointUrl());
            client.connect();
            try {
                DataValue read = client.readValue(0.0, TimestampsToReturn.Neither, NodeId.parse("ns=2;s=switch"));
                DynamicUnionType decoded = (DynamicUnionType) ((ExtensionObject) read.getValue().getValue())
                        .decode(client.getDynamicEncodingContext());
                assertThat(decoded.getValue()).hasValueSatisfying(selected -> {
                    assertThat(selected.fieldName()).isEqualTo("count");
                    assertThat(selected.fieldValue()).isEqualTo(7);
                });
            } finally {
                client.disconnect();
            }
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            worker.stop();
        }
    }

    @Test
    void appliesCanonicalTreeToAnAbstractlyTypedVariable() throws Exception {
        OpcUaProtocolService service = new OpcUaProtocolService();
        WorkerServer worker = new WorkerServer(0, service).start();
        ManagedChannel channel = ManagedChannelBuilder.forAddress("127.0.0.1", worker.port())
                .usePlaintext().build();
        try {
            ProtocolDataSourceGrpc.ProtocolDataSourceBlockingStub blocking =
                    ProtocolDataSourceGrpc.newBlockingStub(channel);
            blocking.configure(ConfigureRequest.newBuilder().setListenPort(freePort()).setSchema(Schema.newBuilder()
                    .addNodes(SchemaNodeMsg.newBuilder().setNodeId("level").setKind("VARIABLE")
                            .setName("Level").setDataTypeNodeId(Identifiers.UInteger.toParseableString()))
                    .build()).build());
            assertThat(blocking.start(StartRequest.getDefaultInstance()).getOk()).isTrue();

            ValueCodec.Encoded tree = ValueCodec.encode(Map.of("type", "UINT32", "value", 7L));
            CountDownLatch completed = new CountDownLatch(1);
            StreamObserver<ValueBatch> values = ProtocolDataSourceGrpc.newStub(channel).applyValues(new StreamObserver<>() {
                @Override public void onNext(Ack ignored) {}
                @Override public void onError(Throwable ignored) { completed.countDown(); }
                @Override public void onCompleted() { completed.countDown(); }
            });
            values.onNext(ValueBatch.newBuilder().addValues(Value.newBuilder().setNodeId("level")
                    .setValueKind(tree.kind().name()).setValueEnc(ByteString.copyFrom(tree.bytes()))).build());
            values.onCompleted();
            assertThat(completed.await(5, TimeUnit.SECONDS)).isTrue();

            OpcUaClient client = OpcUaClient.create(service.opcUaEndpointUrl());
            client.connect();
            try {
                DataValue read = client.readValue(0.0, TimestampsToReturn.Neither, NodeId.parse("ns=2;s=level"));
                assertThat(read.getValue().getValue()).isEqualTo(Unsigned.uint(7L));
            } finally {
                client.disconnect();
            }
        } finally {
            channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            worker.stop();
        }
    }

    private static int freePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
