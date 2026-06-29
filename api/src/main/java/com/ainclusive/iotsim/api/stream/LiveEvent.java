package com.ainclusive.iotsim.api.stream;

import java.time.Instant;
import java.util.Objects;

/**
 * One thing to send over a stream. {@code seq} is the per-stream monotonic id sent
 * as the SSE {@code id:} line; transport events that must not advance the client's
 * {@code Last-Event-ID} (heartbeat, resync) use {@link #NO_SEQ}. {@code data} is
 * serialized to the SSE {@code data:} line as JSON.
 */
public record LiveEvent(long seq, String type, Object data, Instant at) {

    /** Sentinel {@code seq} for events that carry no SSE id (heartbeat, resync). */
    public static final long NO_SEQ = -1L;

    public LiveEvent {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(at, "at");
    }

    public boolean hasSeq() {
        return seq >= 0;
    }
}
