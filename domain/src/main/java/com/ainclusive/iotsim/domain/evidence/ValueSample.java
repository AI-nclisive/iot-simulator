package com.ainclusive.iotsim.domain.evidence;

import java.time.Instant;

/**
 * One value-timeline entry in an evidence artifact: a neutral value at a point in
 * time. {@code value} is the encoded reading ({@code null} when missing);
 * {@code qualityReason} is an optional quality code.
 */
public record ValueSample(
        String nodeId,
        Instant sourceTime,
        Object value,
        String quality,
        String qualityReason) {
}
