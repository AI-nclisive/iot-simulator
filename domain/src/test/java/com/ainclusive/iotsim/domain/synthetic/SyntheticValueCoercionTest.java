package com.ainclusive.iotsim.domain.synthetic;

import static com.ainclusive.iotsim.protocolmodel.DataType.BOOL;
import static com.ainclusive.iotsim.protocolmodel.DataType.BYTES;
import static com.ainclusive.iotsim.protocolmodel.DataType.DATETIME;
import static com.ainclusive.iotsim.protocolmodel.DataType.EXPANDED_NODE_ID;
import static com.ainclusive.iotsim.protocolmodel.DataType.FLOAT32;
import static com.ainclusive.iotsim.protocolmodel.DataType.FLOAT64;
import static com.ainclusive.iotsim.protocolmodel.DataType.GUID;
import static com.ainclusive.iotsim.protocolmodel.DataType.INT16;
import static com.ainclusive.iotsim.protocolmodel.DataType.INT32;
import static com.ainclusive.iotsim.protocolmodel.DataType.INT64;
import static com.ainclusive.iotsim.protocolmodel.DataType.NODE_ID;
import static com.ainclusive.iotsim.protocolmodel.DataType.QUALIFIED_NAME;
import static com.ainclusive.iotsim.protocolmodel.DataType.STATUS_CODE;
import static com.ainclusive.iotsim.protocolmodel.DataType.STRING;
import static com.ainclusive.iotsim.protocolmodel.DataType.UINT16;
import static com.ainclusive.iotsim.protocolmodel.DataType.UINT32;
import static com.ainclusive.iotsim.protocolmodel.DataType.UINT64;
import static com.ainclusive.iotsim.protocolmodel.DataType.XML_ELEMENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;

class SyntheticValueCoercionTest {

    @Test
    void roundsAndClampsIntegerTypesToTheirRange() {
        assertThat(SyntheticValueCoercion.coerce(2.6, INT16)).isEqualTo(3L);
        assertThat(SyntheticValueCoercion.coerce(40000.0, INT16)).isEqualTo(32767L);
        assertThat(SyntheticValueCoercion.coerce(-40000.0, INT16)).isEqualTo(-32768L);
        assertThat(SyntheticValueCoercion.coerce(70000.0, UINT16)).isEqualTo(65535L);
        assertThat(SyntheticValueCoercion.coerce(-5.0, UINT16)).isEqualTo(0L);
        assertThat(SyntheticValueCoercion.coerce(-1.0, UINT64)).isEqualTo(0L);
        assertThat(SyntheticValueCoercion.coerce(123.4, INT32)).isEqualTo(123L);
    }

    @Test
    void uint32HasItsOwnRangeAndInt64SaturatesBeyondDoublePrecision() {
        assertThat(SyntheticValueCoercion.coerce(100.5, UINT32)).isEqualTo(101L);
        assertThat(SyntheticValueCoercion.coerce(5_000_000_000.0, UINT32)).isEqualTo(4_294_967_295L);
        assertThat(SyntheticValueCoercion.coerce(-1.0, UINT32)).isEqualTo(0L);

        assertThat(SyntheticValueCoercion.coerce(42.4, INT64)).isEqualTo(42L);
        assertThat(SyntheticValueCoercion.coerce(1e30, INT64)).isEqualTo(Long.MAX_VALUE);
        assertThat(SyntheticValueCoercion.coerce(-1e30, INT64)).isEqualTo(Long.MIN_VALUE);
    }

    @Test
    void floatTypesProduceDoubles() {
        assertThat(SyntheticValueCoercion.coerce(3.5, FLOAT64)).isEqualTo(3.5);
        assertThat(SyntheticValueCoercion.coerce(3.14159, FLOAT32)).isEqualTo((double) (float) 3.14159);
    }

    @Test
    void boolFromNumberThresholdsAtZeroAndPassesBooleansThrough() {
        assertThat(SyntheticValueCoercion.coerce(1.0, BOOL)).isEqualTo(true);
        assertThat(SyntheticValueCoercion.coerce(0.0, BOOL)).isEqualTo(false);
        assertThat(SyntheticValueCoercion.coerce(Boolean.TRUE, BOOL)).isEqualTo(true);
    }

    @Test
    void stringFromAnyValueUsesToString() {
        assertThat(SyntheticValueCoercion.coerce(3.5, STRING)).isEqualTo("3.5");
        assertThat(SyntheticValueCoercion.coerce("idle", STRING)).isEqualTo("idle");
    }

    @Test
    void rejectsNonNumberForNumericType() {
        assertThatIllegalArgumentException().isThrownBy(() -> SyntheticValueCoercion.coerce("nope", INT16));
    }

    // ─── IS-168: structural/identifier types (CONSTANT-only upstream) ────────────

    @Test
    void stringShapedStructuralTypesPassThroughAsStrings() {
        assertThat(SyntheticValueCoercion.coerce("2:Foo", QUALIFIED_NAME)).isEqualTo("2:Foo");
        assertThat(SyntheticValueCoercion.coerce("ns=2;s=Foo", NODE_ID)).isEqualTo("ns=2;s=Foo");
        assertThat(SyntheticValueCoercion.coerce("ns=2;s=Foo", EXPANDED_NODE_ID)).isEqualTo("ns=2;s=Foo");
        assertThat(SyntheticValueCoercion.coerce("<a/>", XML_ELEMENT)).isEqualTo("<a/>");
    }

    @Test
    void guidValidatesAsAUuidString() {
        String guid = "11111111-1111-1111-1111-111111111111";
        assertThat(SyntheticValueCoercion.coerce(guid, GUID)).isEqualTo(guid);
        assertThatIllegalArgumentException().isThrownBy(() -> SyntheticValueCoercion.coerce("not-a-guid", GUID));
    }

    @Test
    void statusCodeAndDatetimeCoerceToLongLikeIntegerTypes() {
        assertThat(SyntheticValueCoercion.coerce(0.0, STATUS_CODE)).isEqualTo(0L);
        assertThat(SyntheticValueCoercion.coerce(1_753_000_000_000.0, DATETIME)).isEqualTo(1_753_000_000_000L);
    }

    @Test
    void bytesAcceptsAByteArrayAndRejectsOtherwise() {
        byte[] raw = {1, 2, 3};
        assertThat(SyntheticValueCoercion.coerce(raw, BYTES)).isEqualTo(raw);
        assertThatIllegalArgumentException().isThrownBy(() -> SyntheticValueCoercion.coerce("nope", BYTES));
    }
}
