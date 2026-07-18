package com.ainclusive.iotsim.domain.synthetic;

import com.ainclusive.iotsim.protocolmodel.DataType;

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
 * {@code EXPANDED_NODE_ID}, {@code XML_ELEMENT}) are not generable in v1 and are
 * rejected upstream by {@link SyntheticVariable}, so this coercion never sees them.
 */
final class SyntheticValueCoercion {

    private SyntheticValueCoercion() {}

    static Object coerce(Object raw, DataType type) {
        return switch (type) {
            case BOOL -> toBool(raw);
            case STRING, LOCALIZED_TEXT -> String.valueOf(raw);
            case FLOAT64 -> asNumber(raw, type).doubleValue();
            case FLOAT32 -> (double) asNumber(raw, type).floatValue();
            case INT8 -> clampToLong(raw, type, Byte.MIN_VALUE, Byte.MAX_VALUE);
            case UINT8 -> clampToLong(raw, type, 0, 255);
            case INT16 -> clampToLong(raw, type, Short.MIN_VALUE, Short.MAX_VALUE);
            case UINT16 -> clampToLong(raw, type, 0, 65_535);
            case INT32 -> clampToLong(raw, type, Integer.MIN_VALUE, Integer.MAX_VALUE);
            case UINT32 -> clampToLong(raw, type, 0, 4_294_967_295L);
            case INT64 -> clampToLong(raw, type, Long.MIN_VALUE, Long.MAX_VALUE);
            case UINT64 -> clampToLong(raw, type, 0, Long.MAX_VALUE);
            case BYTES, DATETIME, GUID, STATUS_CODE, QUALIFIED_NAME, NODE_ID, EXPANDED_NODE_ID, XML_ELEMENT ->
                    throw new IllegalArgumentException("synthetic generation does not support " + type);
        };
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
