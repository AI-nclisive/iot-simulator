package com.ainclusive.iotsim.persistence.sample;

import java.time.OffsetDateTime;

/** Persistence-level projection of a {@code samples} row. */
public record SampleRow(
        String id,
        String projectId,
        String derivedFromRecordingId,
        String name,
        String selection,
        String tags,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        long version) {}
