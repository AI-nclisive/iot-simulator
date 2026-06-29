package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

class LiveStreamTest {

    private static final Executor INLINE = Runnable::run;

    private Subscriber sub(RecordingSink sink) {
        return new Subscriber(sink, 64, INLINE);
    }

    @Test
    void fansPublishedEventsToSubscribers() {
        LiveStream stream = new LiveStream(8);
        RecordingSink sink = new RecordingSink();
        stream.addSubscriber(sub(sink), null);

        stream.publish("SOURCE_START", "x", Instant.EPOCH);

        assertThat(sink.sent).hasSize(1);
        assertThat(sink.sent.get(0).seq()).isEqualTo(0L);
        assertThat(sink.sent.get(0).type()).isEqualTo("SOURCE_START");
    }

    @Test
    void assignsMonotonicSeqAndCapsBuffer() {
        LiveStream stream = new LiveStream(2);
        for (int i = 0; i < 5; i++) {
            assertThat(stream.publish("X", i, Instant.EPOCH).seq()).isEqualTo((long) i);
        }
        // Buffer holds only the last 2 (seq 3,4); a subscriber from seq 1 needs the evicted seq 2 -> resync.
        RecordingSink late = new RecordingSink();
        stream.addSubscriber(sub(late), "1");
        assertThat(late.sent).extracting(LiveEvent::type).containsExactly(LiveStream.RESYNC);
    }

    @Test
    void replaysBufferedTailAfterLastEventId() {
        LiveStream stream = new LiveStream(8);
        for (int i = 0; i < 4; i++) {
            stream.publish("X", i, Instant.EPOCH); // seq 0..3
        }
        RecordingSink resumed = new RecordingSink();
        stream.addSubscriber(sub(resumed), "1"); // expect replay of seq 2,3

        assertThat(resumed.sent).extracting(LiveEvent::seq).containsExactly(2L, 3L);
    }

    @Test
    void liveOnlyWhenNoLastEventId() {
        LiveStream stream = new LiveStream(8);
        stream.publish("X", 0, Instant.EPOCH);
        RecordingSink fresh = new RecordingSink();
        stream.addSubscriber(sub(fresh), null);

        assertThat(fresh.sent).isEmpty(); // no backlog, only future events
        stream.publish("X", 1, Instant.EPOCH);
        assertThat(fresh.sent).extracting(LiveEvent::seq).containsExactly(1L);
    }

    @Test
    void emitsResyncOnNonNumericLastEventId() {
        LiveStream stream = new LiveStream(8);
        stream.publish("X", 0, Instant.EPOCH);
        RecordingSink bad = new RecordingSink();
        stream.addSubscriber(sub(bad), "not-a-number");

        assertThat(bad.sent).extracting(LiveEvent::type).containsExactly(LiveStream.RESYNC);
        assertThat(bad.sent.get(0).hasSeq()).isFalse();
    }
}
