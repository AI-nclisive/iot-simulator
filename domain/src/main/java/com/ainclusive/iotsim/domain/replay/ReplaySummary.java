package com.ainclusive.iotsim.domain.replay;

import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;

/**
 * Result of a replay run: how many values were streamed to the source, plus the
 * {@code runId} and {@code evidenceId} of the run opened for it (IS-057) so the
 * caller can fetch or export the evidence.
 *
 * <p>{@code deterministicSettings} echoes back the seed/startTime used so the
 * caller can reproduce or audit the run (IS-069).
 */
public record ReplaySummary(
        String recordingId,
        String dataSourceId,
        long valueCount,
        String runId,
        String evidenceId,
        DeterministicSettings deterministicSettings) {
}
