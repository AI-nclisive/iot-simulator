package com.ainclusive.iotsim.persistence.recording;

import java.time.OffsetDateTime;

/**
 * Persistence-level projection of a {@code recordings} row.
 *
 * <p>{@code protocol} (IS-160) is the protocol type the recording was captured under
 * (e.g. {@code OPC_UA}, {@code MODBUS_TCP}) and is what replay/import compatibility is
 * checked against — {@code dataSourceId} is now optional (nullable) and only kept as a
 * "captured from" reference; a recording is never bound to that exact instance.
 */
public record RecordingRow(
        String id,
        String projectId,
        String dataSourceId,
        String protocol,
        int schemaVersion,
        String origin,
        String scanType,
        String name,
        OffsetDateTime timeStart,
        OffsetDateTime timeEnd,
        long valueCount,
        long sizeBytes,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        long version) {
}
