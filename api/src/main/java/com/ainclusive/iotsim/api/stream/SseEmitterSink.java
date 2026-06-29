package com.ainclusive.iotsim.api.stream;

import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/** {@link EventSink} backed by a servlet {@code SseEmitter}, with JSON data lines. */
public final class SseEmitterSink implements EventSink {

    private final SseEmitter emitter;
    private final ObjectMapper json;

    public SseEmitterSink(SseEmitter emitter, ObjectMapper json) {
        this.emitter = emitter;
        this.json = json;
    }

    @Override
    public void send(LiveEvent event) throws IOException {
        SseEmitter.SseEventBuilder builder = SseEmitter.event()
                .name(event.type())
                .data(json.writeValueAsString(event.data()), MediaType.APPLICATION_JSON);
        if (event.hasSeq()) {
            builder.id(Long.toString(event.seq()));
        }
        emitter.send(builder);
    }

    @Override
    public void complete() {
        emitter.complete();
    }
}
