package com.ainclusive.iotsim.domain.replay;

/**
 * Result of a replay run: how many values were streamed to the source, plus the
 * {@code runId} and {@code evidenceId} of the run opened for it (IS-057) so the
 * caller can fetch or export the evidence.
 */
public record ReplaySummary(
        String recordingId,
        String dataSourceId,
        long valueCount,
        String runId,
        String evidenceId) {
}
