package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import java.util.Map;
import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.UInteger;
import org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link OpcUaTypes#neutralTypeOf}, including named standard
 * subtypes with a distinct NodeId that must survive scan unchanged.
 */
class OpcUaTypesTest {

    @Test
    void preservesNamedStandardSubtypeNodeIdsInsteadOfCollapsingToTheirParents() {
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.UtcTime)).isNull();
        assertThat(OpcUaTypes.neutralTypeOf(org.eclipse.milo.opcua.stack.core.types.builtin.NodeId.parse("ns=0;i=293"))).isNull();
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.Duration)).isNull();
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.IntegerId)).isNull();
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.Counter)).isNull();
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.NumericRange)).isNull();
        assertThat(OpcUaTypes.neutralTypeOf(org.eclipse.milo.opcua.stack.core.types.builtin.NodeId.parse("ns=0;i=292"))).isNull();
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.LocaleId)).isNull();
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.NormalizedString)).isNull();
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.DecimalString)).isNull();
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.DurationString)).isNull();
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.TimeString)).isNull();
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.DateString)).isNull();
    }

    @Test
    void stillLeavesGenericSubtypesAndUnknownTypesUnresolved() {
        // BaseDataType (Variant) isn't a concrete built-in or a listed well-known
        // subtype -- must stay null/unknown, not silently coerced.
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.BaseDataType)).isNull();
        assertThat(OpcUaTypes.neutralTypeOf(null)).isNull();
    }

    @Test
    void codecKindOfAbstractIsTree() {
        assertThat(OpcUaTypes.codecKind("ABSTRACT")).isEqualTo(ValueCodec.Kind.TREE);
    }

    @Test
    void capturesAndReplaysAbstractValuesWithATypeDiscriminator() {
        Map<String, Object> capturedInt32 = OpcUaTypes.fromOpcUaVariant(42);
        assertThat(capturedInt32).containsEntry("type", "INT32").containsEntry("value", 42L);
        assertThat(OpcUaTypes.toOpcUaVariant(capturedInt32)).isEqualTo(42);

        UInteger uint32 = Unsigned.uint(7L);
        Map<String, Object> capturedUInt32 = OpcUaTypes.fromOpcUaVariant(uint32);
        assertThat(capturedUInt32).containsEntry("type", "UINT32").containsEntry("value", 7L);
        assertThat(OpcUaTypes.toOpcUaVariant(capturedUInt32)).isEqualTo(uint32);

        Map<String, Object> capturedString = OpcUaTypes.fromOpcUaVariant("hello");
        assertThat(capturedString).containsEntry("type", "STRING").containsEntry("value", "hello");
        assertThat(OpcUaTypes.toOpcUaVariant(capturedString)).isEqualTo("hello");
    }

    @Test
    void capturesAndReplaysAMissingAbstractValueAsNull() {
        Map<String, Object> captured = OpcUaTypes.fromOpcUaVariant(null);
        assertThat(captured.get("type")).isNull();
        assertThat(captured.get("value")).isNull();
        assertThat(OpcUaTypes.toOpcUaVariant(captured)).isNull();
    }

    @Test
    void rejectsAnUnsupportedConcreteTypeForAbstractCapture() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> OpcUaTypes.fromOpcUaVariant(new Object()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
