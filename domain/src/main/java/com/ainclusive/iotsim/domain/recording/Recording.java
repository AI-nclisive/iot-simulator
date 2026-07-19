package com.ainclusive.iotsim.domain.recording;

import java.time.Instant;

/**
 * Captured real data over time from a data-source (backend-specs/03).
 *
 * <p>Scoped to a {@code protocol} type, not to the specific {@code dataSourceId} it was
 * captured from (IS-160) — {@code dataSourceId} is kept only as an optional "originally
 * captured from" reference; replay/import compatibility is checked against {@code protocol}.
 */
public record Recording(
        String id,
        String projectId,
        String dataSourceId,
        String protocol,
        int schemaVersion,
        String origin,
        String scanType,
        String name,
        long valueCount,
        long sizeBytes,
        Instant createdAt,
        String createdBy,
        long version,
        Instant lastUsedAt,
        boolean hasDependents) {
}
