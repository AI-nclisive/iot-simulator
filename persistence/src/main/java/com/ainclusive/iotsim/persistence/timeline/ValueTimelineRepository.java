package com.ainclusive.iotsim.persistence.timeline;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;

/**
 * Append-optimized value timeline: every value change is captured (no sampling);
 * time-ordered range reads serve replay and evidence (backend-specs/04).
 */
public interface ValueTimelineRepository {

    /** Appends a batch of values to a recording. Returns the number written. */
    long append(String recordingId, List<NeutralValue> values);

    /** Time-ordered values in [from, to]. */
    List<NeutralValue> readRange(String recordingId, Instant from, Instant to);

    /** All values for a recording, time-ordered (used by replay). */
    List<NeutralValue> readAll(String recordingId);

    long count(String recordingId);

    /**
     * Keyset-paginated read: returns up to {@code limit} entries with {@code seq > afterSeq},
     * ordered by seq. Pass {@code afterSeq = -1} to start from the beginning (IS-134).
     */
    List<ValueTimelineEntry> readPage(String recordingId, long afterSeq, int limit);

    /** A single timeline row with its sequence number (used for cursor-based pagination). */
    record ValueTimelineEntry(long seq, String parameterPath, NeutralValue value) {}
}
