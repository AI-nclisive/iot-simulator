package com.epam.iotsim.protocolmodel;

import java.time.Instant;
import java.util.Objects;

/**
 * A single observed or produced value for one variable node.
 *
 * <p>{@code sourceTime} is the authoritative ordering key and drives determinism.
 * See {@code backend-specs/01_PROTOCOL_NEUTRAL_MODEL.md} §3.
 *
 * @param value         encoded per the node's {@link DataType}; {@code null} when
 *                      the value is missing (e.g. a MISSING_VALUE fault)
 * @param qualityReason optional reason code (STALE, COMM_FAILURE, OUT_OF_RANGE,
 *                      FAULT_INJECTED, ...); may be {@code null}
 */
public record NeutralValue(
        String nodeId,
        Instant sourceTime,
        Object value,
        Quality quality,
        String qualityReason) {

    public NeutralValue {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(sourceTime, "sourceTime");
        Objects.requireNonNull(quality, "quality");
    }

    public static NeutralValue good(String nodeId, Instant sourceTime, Object value) {
        return new NeutralValue(nodeId, sourceTime, value, Quality.GOOD, null);
    }
}
