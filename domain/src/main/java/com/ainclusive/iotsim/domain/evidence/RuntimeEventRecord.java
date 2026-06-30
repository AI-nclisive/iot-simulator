package com.ainclusive.iotsim.domain.evidence;

import java.time.Instant;

/**
 * One runtime-event entry in an evidence artifact. {@code payloadJson} is the raw
 * event payload as a JSON document; the artifact writer embeds it as a nested
 * object.
 */
public record RuntimeEventRecord(
        String type,
        Instant at,
        String dataSourceId,
        String payloadJson) {
}
