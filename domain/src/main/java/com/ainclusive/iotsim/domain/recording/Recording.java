package com.ainclusive.iotsim.domain.recording;

import java.time.Instant;

/** Captured real data over time from a data-source (backend-specs/03). */
public record Recording(
        String id,
        String projectId,
        String dataSourceId,
        int schemaVersion,
        String origin,
        long valueCount,
        Instant createdAt,
        String createdBy,
        long version) {
}
