package com.ainclusive.iotsim.domain.recording;

import java.time.Instant;
import java.util.List;

/**
 * Manifest embedded in a recording export ZIP (IS-070, backend-specs/06_ARTIFACT_FORMATS.md).
 * Carries the format version, source schema snapshot, time range, value count, and checksums
 * so a re-import is fully lossless and replay-ready.
 */
public record RecordingExportManifest(
        String formatVersion,
        String recordingId,
        String projectId,
        String dataSourceId,
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
