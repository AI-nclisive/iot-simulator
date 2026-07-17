package com.ainclusive.iotsim.domain.recording;

import java.time.Instant;
import java.util.List;

/**
 * Manifest embedded in a recording export ZIP (IS-070, backend-specs/06_ARTIFACT_FORMATS.md).
 * Carries the format version, source schema snapshot, time range, value count, and checksums
 * so a re-import is fully lossless and replay-ready.
 *
 * <p>{@code protocol} (IS-160) is the required protocol type the recording is scoped to;
 * {@code dataSourceId} is now optional metadata only ("originally captured from") — import
 * succeeds even when it no longer resolves to an existing data source, as long as
 * {@code protocol} is present and valid.
 */
public record RecordingExportManifest(
        String formatVersion,
        String recordingId,
        String projectId,
        String dataSourceId,
        String protocol,
        int schemaVersion,
        String origin,
        String name,
        Instant exportedAt,
        Instant timeStart,
        Instant timeEnd,
        long valueCount,
        List<String> nodeIds) {

    public static final String FORMAT_VERSION = "1.0.0";
}
