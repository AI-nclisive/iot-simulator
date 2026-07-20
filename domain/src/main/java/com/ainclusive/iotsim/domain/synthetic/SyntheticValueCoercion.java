package com.ainclusive.iotsim.domain.synthetic;

import com.ainclusive.iotsim.protocolmodel.DataType;
import java.util.UUID;

/**
 * Maps a pattern's raw output ({@code Double} for numeric patterns, or an element
 * value for {@link SyntheticPattern.EnumCycle}/{@link SyntheticPattern.StepSequence})
 * onto a node's neutral {@link DataType}, matching the boxed Java types the value
 * timeline stores (see {@code ValueCodec}): integer types → {@link Long} (rounded and
 * clamped to the type's range), float types → {@link Double}, {@code BOOL} →
 * {@link Boolean}, {@code STRING}/{@code LOCALIZED_TEXT} → {@link String}.
 *
 * <p>{@code BYTES}, {@code DATETIME}, and the identifier/structural types
 * ({@code GUID}, {@code STATUS_CODE}, {@code QUALIFIED_NAME}, {@code NODE_ID},
 * {@code EXPANDED_NODE_ID}, {@code XML_ELEMENT}) only reach here via a {@link
 * SyntheticPattern.Constant} (IS-168) — {@link SyntheticVariable} rejects any
 * other pattern for them upstream, so their raw value is always the fixed
 * constant payload, never a generated series.
 */
final class SyntheticValueCoercion {

    private SyntheticValueCoercion() {}

    static Object coerce(Object raw, DataType type) {
        return switch (type) {
            case BOOL -> toBool(raw);
            case STRING, LOCALIZED_TEXT, QUALIFIED_NAME, NODE_ID, EXPANDED_NODE_ID, XML_ELEMENT ->
                    String.valueOf(raw);
            case GUID -> asGuid(raw);
            case FLOAT64 -> asNumber(raw, type).doubleValue();
            case FLOAT32 -> (double) asNumber(raw, type).floatValue();
            case INT8 -> clampToLong(raw, type, Byte.MIN_VALUE, Byte.MAX_VALUE);
            case UINT8 -> clampToLong(raw, type, 0, 255);
            case INT16 -> clampToLong(raw, type, Short.MIN_VALUE, Short.MAX_VALUE);
            case UINT16 -> clampToLong(raw, type, 0, 65_535);
            case INT32 -> clampToLong(raw, type, Integer.MIN_VALUE, Integer.MAX_VALUE);
            case UINT32 -> clampToLong(raw, type, 0, 4_294_967_295L);
            case INT64, DATETIME -> clampToLong(raw, type, Long.MIN_VALUE, Long.MAX_VALUE);
            case UINT64, STATUS_CODE -> clampToLong(raw, type, 0, Long.MAX_VALUE);
            case BYTES -> asBytes(raw);
        };
    }

    private static String asGuid(Object raw) {
        String s = String.valueOf(raw);
        try {
            UUID.fromString(s);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("cannot coerce " + raw + " to GUID: not a valid UUID string", e);
        }
        return s;
    }

    private static byte[] asBytes(Object raw) {
        if (raw instanceof byte[] bytes) {
            return bytes;
        }
        throw new IllegalArgumentException("cannot coerce " + raw + " to BYTES: expected a byte[] value");
    }

    private static Object toBool(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        return asNumber(raw, DataType.BOOL).doubleValue() != 0.0;
    }

    private static long clampToLong(Object raw, DataType type, long min, long max) {
        return Math.clamp(Math.round(asNumber(raw, type).doubleValue()), min, max);
    }

    private static Number asNumber(Object raw, DataType type) {
        if (raw instanceof Number n) {
            return n;
        }
        throw new IllegalArgumentException(
                "cannot coerce " + raw + " to " + type + ": expected a numeric value");
    }
}
