package com.ainclusive.iotsim.api.stream;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Subscribe side of the registry — what the SSE controllers depend on. */
public interface LiveStreamSubscriptions {
    SseEmitter subscribe(StreamKey key, String lastEventId);
}
