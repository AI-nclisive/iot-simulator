package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ainclusive.iotsim.workercontract.v1.CaptureRequest;
import com.ainclusive.iotsim.workercontract.v1.DataTypeEnumValueMsg;
import com.ainclusive.iotsim.workercontract.v1.DataTypeMemberMsg;
import com.ainclusive.iotsim.workercontract.v1.Schema;
import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import org.junit.jupiter.api.Test;

class OpcUaCaptureSchemaTest {

    @Test
    void capturesKnownTypeWhenItsOriginalOpcUaDeclarationIsAlsoPresent() {
        CaptureRequest request = CaptureRequest.newBuilder().setSchema(Schema.newBuilder()
                .addNodes(SchemaNodeMsg.newBuilder()
                        .setNodeId("ns=2;s=qname")
                        .setKind("VARIABLE")
                        .setDataType("QUALIFIED_NAME")
                        .setDeclaredDataTypeNodeId("ns=0;i=20")))
                .build();

        assertThat(OpcUaProtocolService.captureNodes(request))
                .containsExactly(new OpcUaCapture.NodeSpec("ns=2;s=qname", "QUALIFIED_NAME"));
    }

    @Test
    void resolvesSchemaOwnedEnumToIntegerCaptureEncoding() {
        CaptureRequest request = CaptureRequest.newBuilder().setSchema(Schema.newBuilder()
                .addNodes(SchemaNodeMsg.newBuilder()
                        .setNodeId("ns=2;i=9001")
                        .setKind("DATA_TYPE")
                        .addDataTypeEnumValues(DataTypeEnumValueMsg.newBuilder()
                                .setName("Stopped")
                                .setValue(0)))
                .addNodes(SchemaNodeMsg.newBuilder()
                        .setNodeId("ns=2;s=state")
                        .setKind("VARIABLE")
                        .setDataTypeNodeId("ns=2;i=9001")))
                .build();

        assertThat(OpcUaProtocolService.captureNodes(request))
                .containsExactly(new OpcUaCapture.NodeSpec("ns=2;s=state", "INT32"));
    }

    @Test
    void rejectsStructuredOrMissingNativeEncodingBeforeConnecting() {
        CaptureRequest request = CaptureRequest.newBuilder().setSchema(Schema.newBuilder()
                .addNodes(SchemaNodeMsg.newBuilder()
                        .setNodeId("ns=2;s=reading")
                        .setKind("VARIABLE")
                        .setDataTypeNodeId("ns=2;i=7001")))
                .build();

        assertThatIllegalArgumentException().isThrownBy(() -> OpcUaProtocolService.captureNodes(request))
                .withMessageContaining("without an executable encoding")
                .withMessageContaining("ns=2;i=7001");
    }

    @Test
    void rejectsOptionSetInsteadOfTreatingItsBitsAsAnEnum() {
        CaptureRequest request = CaptureRequest.newBuilder().setSchema(Schema.newBuilder()
                .addNodes(SchemaNodeMsg.newBuilder()
                        .setNodeId("ns=2;i=7001")
                        .setKind("DATA_TYPE")
                        .setNativeTypeKind("OPTION_SET")
                        .addDataTypeEnumValues(DataTypeEnumValueMsg.newBuilder()
                                .setName("Enabled")
                                .setValue(1)))
                .addNodes(SchemaNodeMsg.newBuilder()
                        .setNodeId("ns=2;s=options")
                        .setKind("VARIABLE")
                        .setDataTypeNodeId("ns=2;i=7001")))
                .build();

        assertThatIllegalArgumentException().isThrownBy(() -> OpcUaProtocolService.captureNodes(request))
                .withMessageContaining("without an executable encoding")
                .withMessageContaining("ns=2;i=7001");
    }

    @Test
    void resolvesStructureWithDefaultEncodingToRawBinaryCapture() {
        CaptureRequest request = CaptureRequest.newBuilder().setSchema(Schema.newBuilder()
                .addNodes(SchemaNodeMsg.newBuilder()
                        .setNodeId("ns=2;i=7001")
                        .setKind("DATA_TYPE")
                        .addDataTypeMembers(DataTypeMemberMsg.newBuilder().setName("value").setDataType("INT32"))
                        .setDataTypeDefaultEncodingId("ns=2;i=7002"))
                .addNodes(SchemaNodeMsg.newBuilder()
                        .setNodeId("ns=2;s=reading")
                        .setKind("VARIABLE")
                        .setDataTypeNodeId("ns=2;i=7001")))
                .build();

        assertThat(OpcUaProtocolService.captureNodes(request))
                .containsExactly(new OpcUaCapture.NodeSpec("ns=2;s=reading", null, "ns=2;i=7002"));
    }
}
