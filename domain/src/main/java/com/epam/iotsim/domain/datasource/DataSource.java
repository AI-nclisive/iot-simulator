package com.epam.iotsim.domain.datasource;

import java.time.Instant;

/**
 * A simulated instrument source. {@code runtimeState} is derived from the
 * supervisor and not persisted. See backend-specs/03_DOMAIN_MODEL.md.
 */
public record DataSource(
        String id,
        String projectId,
        String name,
        Protocol protocol,
        SourceBasis basis,
        String schemaId,
        Integer schemaVersion,
        String endpoint,
        String runtimeConfig,
        boolean enabled,
        RuntimeState runtimeState,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        long version) {
}
