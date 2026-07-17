package com.ainclusive.iotsim.domain.recording;

import com.ainclusive.iotsim.protocolmodel.SchemaNode;
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
 *
 * <p>{@code schemaNodes} (IS-161) is the recording's own captured schema snapshot, carried
 * through export/import so the recording stays fully self-contained and schema-serving
 * never depends on a live lookup against {@code dataSourceId}. {@code null}/absent on
 * import (bundles exported before IS-161) is treated as "no schema captured".
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
        List<String> nodeIds,
        List<SchemaNode> schemaNodes) {

    public static final String FORMAT_VERSION = "1.0.0";
}
