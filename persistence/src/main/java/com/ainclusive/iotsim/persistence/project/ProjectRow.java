package com.ainclusive.iotsim.persistence.project;

import java.time.OffsetDateTime;

/** Persistence-level projection of a {@code projects} row. */
public record ProjectRow(
        String id,
        String name,
        String description,
        String status,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        long version) {
}
