package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class StreamControllersTest {

    record Sub(StreamKey key, String lastEventId) {}

    static final class RecordingSubscriptions implements LiveStreamSubscriptions {
        final List<Sub> calls = new CopyOnWriteArrayList<>();

        @Override
        public SseEmitter subscribe(StreamKey key, String lastEventId) {
            calls.add(new Sub(key, lastEventId));
            return new SseEmitter(0L);
        }

        @Override
        public SseEmitter subscribe(StreamKey key, String lastEventId, java.util.List<LiveEvent> initial) {
            return subscribe(key, lastEventId);
        }
    }

    @Test
    void runtimeControllerSubscribesToProjectRuntimeStream() {
        RecordingSubscriptions subs = new RecordingSubscriptions();
        SseEmitter emitter = new RuntimeStreamController(subs).streamRuntime("p1", "42");

        assertThat(emitter).isNotNull();
        assertThat(subs.calls).singleElement()
                .isEqualTo(new Sub(StreamKey.runtime("p1"), "42"));
    }

    @Test
    void clientControllerSubscribesToDataSourceClientsStream() {
        RecordingSubscriptions subs = new RecordingSubscriptions();
        SseEmitter emitter = new ClientStreamController(subs).streamClients("d9", null);

        assertThat(emitter).isNotNull();
        assertThat(subs.calls).singleElement()
                .isEqualTo(new Sub(StreamKey.clients("d9"), null));
    }
}
