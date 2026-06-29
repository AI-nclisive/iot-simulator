package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ValuesStreamControllerTest {

    record Sub(StreamKey key, String lastEventId, List<LiveEvent> initial) {}

    static final class RecordingSubscriptions implements LiveStreamSubscriptions {
        final List<Sub> calls = new CopyOnWriteArrayList<>();
        @Override public SseEmitter subscribe(StreamKey key, String lastEventId) {
            return subscribe(key, lastEventId, List.of());
        }
        @Override public SseEmitter subscribe(StreamKey key, String lastEventId, List<LiveEvent> initial) {
            calls.add(new Sub(key, lastEventId, initial));
            return new SseEmitter(0L);
        }
    }

    @Test
    void subscribesToValuesStreamWithSnapshotAndNoLastEventId() {
        RecordingSubscriptions subs = new RecordingSubscriptions();
        LiveValueStore store = new LiveValueStore();
        store.record("d1", List.of(NeutralValue.good("n1", Instant.EPOCH, 7)));

        SseEmitter emitter = new ValuesStreamController(subs, store).streamValues("d1", "42");

        assertThat(emitter).isNotNull();
        assertThat(subs.calls).singleElement().satisfies(c -> {
            assertThat(c.key()).isEqualTo(StreamKey.values("d1"));
            assertThat(c.lastEventId()).isNull(); // snapshot supersedes Last-Event-ID
            assertThat(c.initial()).singleElement().satisfies(ev -> {
                assertThat(ev.type()).isEqualTo("values-snapshot");
                assertThat(ev.hasSeq()).isFalse();
                assertThat((List<?>) ev.data()).hasSize(1);
            });
        });
    }
}
