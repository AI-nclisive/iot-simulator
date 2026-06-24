package com.epam.iotsim.domain.replay;

/** Result of a replay run: how many values were streamed to the source. */
public record ReplaySummary(String recordingId, String dataSourceId, long valueCount) {
}
