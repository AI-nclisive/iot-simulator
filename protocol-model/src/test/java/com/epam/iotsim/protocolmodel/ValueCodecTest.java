package com.epam.iotsim.protocolmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.epam.iotsim.protocolmodel.ValueCodec.Encoded;
import org.junit.jupiter.api.Test;

class ValueCodecTest {

    @Test
    void roundTripsScalars() {
        assertThat(reencode(true)).isEqualTo(true);
        assertThat(reencode(42L)).isEqualTo(42L);
        assertThat(reencode(7)).isEqualTo(7L); // ints decode to Long
        assertThat(reencode(3.5d)).isEqualTo(3.5d);
        assertThat(reencode("hello")).isEqualTo("hello");
    }

    @Test
    void roundTripsBytes() {
        byte[] raw = {1, 2, 3, 4};
        Encoded e = ValueCodec.encode(raw);
        assertThat(e.kind()).isEqualTo(ValueCodec.Kind.BYTES);
        assertThat((byte[]) ValueCodec.decode(e.kind(), e.bytes())).containsExactly(raw);
    }

    @Test
    void encodesKinds() {
        assertThat(ValueCodec.encode(1).kind()).isEqualTo(ValueCodec.Kind.INT);
        assertThat(ValueCodec.encode(1.0).kind()).isEqualTo(ValueCodec.Kind.NUM);
        assertThat(ValueCodec.encode(false).kind()).isEqualTo(ValueCodec.Kind.BOOL);
        assertThat(ValueCodec.encode("x").kind()).isEqualTo(ValueCodec.Kind.TEXT);
    }

    private static Object reencode(Object value) {
        Encoded e = ValueCodec.encode(value);
        return ValueCodec.decode(e.kind(), e.bytes());
    }
}
