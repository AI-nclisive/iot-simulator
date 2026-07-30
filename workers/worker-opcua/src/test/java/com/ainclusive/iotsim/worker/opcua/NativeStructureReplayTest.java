package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.eclipse.milo.opcua.stack.core.types.builtin.ByteString;
import org.eclipse.milo.opcua.stack.core.types.builtin.DataValue;
import org.eclipse.milo.opcua.stack.core.types.builtin.ExtensionObject;
import org.eclipse.milo.opcua.stack.core.types.builtin.NodeId;
import org.junit.jupiter.api.Test;

class NativeStructureReplayTest {

    @Test
    void keepsTheDeclaredEncodingAndOriginalBinaryBody() {
        NodeId encodingId = NodeId.parse("ns=2;i=6001");

        ExtensionObject value = OpcUaProtocolService.structureValue(encodingId, new byte[] {1, 2, 3});

        assertThat(value.getEncodingId()).isEqualTo(encodingId);
        assertThat(value.getBody()).isEqualTo(ByteString.of(new byte[] {1, 2, 3}));
    }

    @Test
    void rejectsADeclarationWithoutExecutableEncodingMetadata() {
        assertThatThrownBy(() -> OpcUaProtocolService.structureValue(null, new byte[] {1}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default encoding id");
    }

    @Test
    void captureKeepsAnOpaqueStructureBinaryBody() {
        NodeId encodingId = NodeId.parse("ns=2;i=6001");
        byte[] body = {3, 2, 1};

        var captured = OpcUaCapture.toProtoValue(
                new OpcUaCapture.NodeSpec("ns=2;s=structure", null, encodingId.toParseableString()),
                new DataValue(new org.eclipse.milo.opcua.stack.core.types.builtin.Variant(
                        new ExtensionObject(ByteString.of(body), encodingId))));

        assertThat(captured.getValueEnc().toByteArray()).containsExactly(body);
    }

    @Test
    void captureRejectsAnExtensionObjectWithDifferentEncoding() {
        assertThatThrownBy(() -> OpcUaCapture.toProtoValue(
                new OpcUaCapture.NodeSpec("ns=2;s=structure", null, "ns=2;i=6001"),
                new DataValue(new org.eclipse.milo.opcua.stack.core.types.builtin.Variant(
                        new ExtensionObject(ByteString.of(new byte[] {1}), NodeId.parse("ns=2;i=6002"))))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schema declares");
    }
}
