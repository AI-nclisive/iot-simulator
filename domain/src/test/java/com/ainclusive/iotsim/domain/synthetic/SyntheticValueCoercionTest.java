package com.ainclusive.iotsim.domain.synthetic;

import static com.ainclusive.iotsim.protocolmodel.DataType.BOOL;
import static com.ainclusive.iotsim.protocolmodel.DataType.FLOAT32;
import static com.ainclusive.iotsim.protocolmodel.DataType.FLOAT64;
import static com.ainclusive.iotsim.protocolmodel.DataType.INT16;
import static com.ainclusive.iotsim.protocolmodel.DataType.INT32;
import static com.ainclusive.iotsim.protocolmodel.DataType.STRING;
import static com.ainclusive.iotsim.protocolmodel.DataType.UINT16;
import static com.ainclusive.iotsim.protocolmodel.DataType.UINT64;
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
}
