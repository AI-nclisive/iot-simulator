package com.ainclusive.iotsim.api.stream;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;

/**
 * SSE payload for one live value. Mirrors {@link NeutralValue} but renders enum and
 * instant as strings for a stable JSON shape. {@code value} and {@code qualityReason}
 * may be null (missing value / no reason).
 */
public record StreamValue(
        String nodeId, Object value, String quality, String qualityReason, String sourceTime) {

    public static StreamValue from(NeutralValue v) {
        return new StreamValue(
                v.nodeId(), v.value(), v.quality().name(), v.qualityReason(),
                v.sourceTime().toString());
    }
}
