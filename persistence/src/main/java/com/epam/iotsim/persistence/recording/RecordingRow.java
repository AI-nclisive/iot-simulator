package com.epam.iotsim.persistence.recording;

import java.time.OffsetDateTime;

/** Persistence-level projection of a {@code recordings} row. */
public record RecordingRow(
        String id,
        String projectId,
        String dataSourceId,
        int schemaVersion,
        String origin,
        OffsetDateTime timeStart,
        OffsetDateTime timeEnd,
        long valueCount,
        long sizeBytes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        long version) {
}
