package com.ainclusive.iotsim.domain.sample;

import java.time.Instant;
import java.util.List;

/**
 * Manifest embedded in a sample export ZIP (IS-070, backend-specs/06_ARTIFACT_FORMATS.md).
 * Extends the recording manifest with the {@code selection} field that describes the
 * node-subset / time-window used to create the sample.
 */
public record SampleExportManifest(
        String formatVersion,
        String sampleId,
        String projectId,
        String derivedFromRecordingId,
        String name,
        String selection,
        List<String> tags,
        Instant exportedAt,
        long valueCount,
        List<String> nodeIds) {

    public static final String FORMAT_VERSION = "1.0.0";
}
