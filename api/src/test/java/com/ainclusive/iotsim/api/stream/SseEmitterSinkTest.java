package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

class SseEmitterSinkTest {

    /** Captures objects handed to send(...) without a servlet response. */
    static final class CapturingEmitter extends SseEmitter {
        final List<Object> sent = new CopyOnWriteArrayList<>();
        volatile boolean completed;

        @Override
        public void send(SseEventBuilder builder) {
            // Materialize the builder's data items so the test can inspect payloads.
            builder.build().forEach(item -> sent.add(item.getData()));
        }

        @Override
        public void complete() {
            completed = true;
        }
    }

    @Test
    void serializesDataAsJsonAndForwardsComplete() throws Exception {
        CapturingEmitter emitter = new CapturingEmitter();
        SseEmitterSink sink = new SseEmitterSink(emitter, new ObjectMapper());

        sink.send(new LiveEvent(3L, "SOURCE_START", Map.of("dataSourceId", "d1"), Instant.EPOCH));
        sink.complete();

        assertThat(emitter.sent).anySatisfy(d ->
                assertThat(d.toString()).contains("\"dataSourceId\":\"d1\""));
        assertThat(emitter.completed).isTrue();
    }
}
