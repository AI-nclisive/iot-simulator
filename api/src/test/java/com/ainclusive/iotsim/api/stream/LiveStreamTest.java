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

    @Test
    void initialSnapshotIsEnqueuedBeforeLiveEvents() {
        LiveStream stream = new LiveStream(8);
        RecordingSink sink = new RecordingSink();
        Subscriber sub = new Subscriber(sink, 64, Runnable::run);
        LiveEvent snap = new LiveEvent(LiveEvent.NO_SEQ, "values-snapshot", "S", Instant.EPOCH);

        stream.addSubscriber(sub, null, java.util.List.of(snap)); // 3-arg with initial
        stream.publish("values", "L", Instant.EPOCH);             // a live event after

        assertThat(sink.sent).extracting(LiveEvent::type)
                .containsExactly("values-snapshot", "values"); // snapshot first, then live
    }

    @Test
    void supplierSnapshotIsComputedAtRegistrationAndPrecedesLiveEvents() {
        LiveStream stream = new LiveStream(8);
        RecordingSink sink = new RecordingSink();
        Subscriber sub = new Subscriber(sink, 64, Runnable::run);
        LiveEvent snap = new LiveEvent(LiveEvent.NO_SEQ, "clients-snapshot", "S", Instant.EPOCH);

        // The supplier runs under the lock as the subscriber joins; a publish cannot
        // interleave between snapshot and registration, and the snapshot is sent first.
        stream.addSubscriber(sub, null, () -> java.util.List.of(snap));
        stream.publish("CONNECTED", "L", Instant.EPOCH);

        assertThat(sink.sent).extracting(LiveEvent::type)
                .containsExactly("clients-snapshot", "CONNECTED");
    }
}
