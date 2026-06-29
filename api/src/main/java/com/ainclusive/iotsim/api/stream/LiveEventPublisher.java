package com.ainclusive.iotsim.api.stream;

import java.time.Instant;

/** Publish side of the registry — what {@code LiveEventHub} depends on. */
public interface LiveEventPublisher {
    void publish(StreamKey key, String type, Object data, Instant at);
}
