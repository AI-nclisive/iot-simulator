package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.milo.opcua.stack.core.Identifiers;
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
}
