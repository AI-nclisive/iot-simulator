package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.milo.opcua.stack.core.Identifiers;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link OpcUaTypes#neutralTypeOf}, specifically the
 * well-known standard subtype aliases (e.g. {@code UtcTime}) that a live scan
 * against an embedded {@link OpcUaServerRuntime} can't exercise — that harness
 * only ever builds nodes typed with a built-in {@link com.ainclusive.iotsim.protocolmodel.DataType}
 * via {@link OpcUaTypes#dataTypeId}, never a subtype's distinct NodeId.
 */
class OpcUaTypesTest {

    @Test
    void mapsWellKnownStandardSubtypesToTheirParentBuiltinType() {
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.UtcTime)).isEqualTo("DATETIME");
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.Date)).isEqualTo("DATETIME");
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.Duration)).isEqualTo("FLOAT64");
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.IntegerId)).isEqualTo("UINT32");
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.Counter)).isEqualTo("UINT32");
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.NumericRange)).isEqualTo("STRING");
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.Time)).isEqualTo("STRING");
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.LocaleId)).isEqualTo("STRING");
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.NormalizedString)).isEqualTo("STRING");
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.DecimalString)).isEqualTo("STRING");
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.DurationString)).isEqualTo("STRING");
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.TimeString)).isEqualTo("STRING");
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.DateString)).isEqualTo("STRING");
    }

    @Test
    void stillLeavesGenericSubtypesAndUnknownTypesUnresolved() {
        // BaseDataType (Variant) isn't a concrete built-in or a listed well-known
        // subtype -- must stay null/unknown, not silently coerced.
        assertThat(OpcUaTypes.neutralTypeOf(Identifiers.BaseDataType)).isNull();
        assertThat(OpcUaTypes.neutralTypeOf(null)).isNull();
    }
}
