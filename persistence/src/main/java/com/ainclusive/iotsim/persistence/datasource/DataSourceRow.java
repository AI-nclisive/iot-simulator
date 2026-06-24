package com.ainclusive.iotsim.persistence.datasource;

import java.time.OffsetDateTime;

/** Persistence-level projection of a {@code data_sources} row. */
public record DataSourceRow(
        String id,
        String projectId,
        String name,
        String protocol,
        String basis,
        String schemaId,
        Integer schemaVersion,
        String endpoint,
        String runtimeConfig,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        long version) {
}
