package com.ainclusive.iotsim.api.stream;

import java.io.IOException;

/**
 * Where a {@link Subscriber} writes events. Production is {@link SseEmitterSink}
 * over a servlet {@code SseEmitter}; tests use a recording fake so stream logic is
 * verifiable without a servlet.
 */
public interface EventSink {

    /** Writes one event; throws if the underlying connection is gone. */
    void send(LiveEvent event) throws IOException;

    /** Ends the stream (graceful close or backpressure disconnect). */
    void complete();
}
