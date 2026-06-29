package com.ainclusive.iotsim.persistence.clientconnection;

import java.time.OffsetDateTime;

/**
 * Persistence-level projection of a {@code client_connections} row (backend-specs/04).
 * One row per client connection observed at a running data source's protocol
 * endpoint (IS-047/IS-052): {@code connectedAt} is set on connect, {@code
 * disconnectedAt} stays {@code null} while the client is connected and is set on
 * disconnect. Backs connected-client observation and feeds run evidence (IS-057).
 *
 * <p>{@code summaryJson} is an opaque per-connection JSON object (protocol-specific
 * detail); {@code null} maps to an empty object.
 */
public record ClientConnectionRow(
        String id,
        String dataSourceId,
        String clientId,
        OffsetDateTime connectedAt,
        OffsetDateTime disconnectedAt,
        String summaryJson) {
}
