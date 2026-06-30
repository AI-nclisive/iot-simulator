package com.ainclusive.iotsim.domain.evidence;

import java.time.Instant;

/**
 * One activated-fault entry in an evidence artifact. The fault model lands with
 * IS-087; until then this section is present but empty, keeping the format stable.
 */
public record FaultRecord(
        String type,
        Instant at,
        String dataSourceId,
        String detail) {
}
