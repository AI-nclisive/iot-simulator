package com.ainclusive.iotsim.protocolmodel;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.ValueCodec.Encoded;
import java.util.List;
import java.util.Map;
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

    @Test
    void roundTripsCanonicalNativeValueTree() {
        Map<String, Object> source = Map.of(
                "enabled", true,
                "thresholds", List.of(1L, 2.5d),
                "payload", new byte[] {1, 2},
                "nested", Map.of("label", "pump"));

        Encoded encoded = ValueCodec.encode(source);

        assertThat(encoded.kind()).isEqualTo(ValueCodec.Kind.TREE);
        @SuppressWarnings("unchecked")
        Map<String, Object> decoded = (Map<String, Object>) ValueCodec.decode(encoded.kind(), encoded.bytes());
        assertThat(decoded).containsEntry("enabled", true).containsEntry("thresholds", List.of(1L, 2.5d));
        assertThat((byte[]) decoded.get("payload")).containsExactly(1, 2);
        assertThat(decoded.get("nested")).isEqualTo(Map.of("label", "pump"));
    }

    @Test
    void nativeValueTreeEncodingDoesNotDependOnMapIterationOrder() {
        Encoded left = ValueCodec.encode(Map.of("a", 1L, "b", 2L));
        Encoded right = ValueCodec.encode(Map.of("b", 2L, "a", 1L));

        assertThat(left.bytes()).containsExactly(right.bytes());
    }

    private static Object reencode(Object value) {
        Encoded e = ValueCodec.encode(value);
        return ValueCodec.decode(e.kind(), e.bytes());
    }
}
