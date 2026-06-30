package com.ainclusive.iotsim.domain.evidence;

import java.time.Instant;

/**
 * One error entry in an evidence artifact: an error surfaced during the run
 * (derived from runtime events of an error type).
 */
public record ErrorRecord(
        String type,
        Instant at,
        String dataSourceId,
        String detail) {
}
