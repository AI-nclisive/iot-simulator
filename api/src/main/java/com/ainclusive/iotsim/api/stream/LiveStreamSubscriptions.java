package com.ainclusive.iotsim.api.stream;

import java.util.List;
import java.util.function.Supplier;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Subscribe side of the registry — what the SSE controllers depend on. */
public interface LiveStreamSubscriptions {

    SseEmitter subscribe(StreamKey key, String lastEventId);

    /** Subscribe and deliver {@code initial} events to this subscriber before live events. */
    SseEmitter subscribe(StreamKey key, String lastEventId, List<LiveEvent> initial);

    /**
     * Subscribe and seed a snapshot computed <em>at registration time</em> rather than
     * before it, so no event is lost in the gap between reading the snapshot and joining
     * the live stream (the snapshot is the only resync when {@code Last-Event-ID} replay
     * is disabled). {@link LiveStreamRegistry} runs {@code initialSupplier} inside the
     * stream lock for that atomicity; the default here is a best-effort delegation for
     * lightweight fakes.
     */
    default SseEmitter subscribe(
            StreamKey key, String lastEventId, Supplier<List<LiveEvent>> initialSupplier) {
        return subscribe(key, lastEventId, initialSupplier.get());
    }
}
