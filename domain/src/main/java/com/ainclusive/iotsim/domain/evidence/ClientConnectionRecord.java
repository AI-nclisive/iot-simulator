package com.ainclusive.iotsim.domain.evidence;

import java.time.Instant;

/**
 * One client-connection entry in an evidence artifact: a client's connect/disconnect
 * window at a source. {@code disconnectedAt} is {@code null} for a connection still
 * open at run end.
 */
public record ClientConnectionRecord(
        String clientId,
        String dataSourceId,
        Instant connectedAt,
        Instant disconnectedAt) {
}
