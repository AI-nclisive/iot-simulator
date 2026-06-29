package com.ainclusive.iotsim.api.stream;

import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Subscribe side of the registry — what the SSE controllers depend on. */
public interface LiveStreamSubscriptions {

    SseEmitter subscribe(StreamKey key, String lastEventId);

    /** Subscribe and deliver {@code initial} events to this subscriber before live events. */
    SseEmitter subscribe(StreamKey key, String lastEventId, List<LiveEvent> initial);
}
