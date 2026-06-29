package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;

class SubscriberTest {

    /** Runs tasks inline so drains are synchronous and assertions deterministic. */
    private static final Executor INLINE = Runnable::run;

    private static LiveEvent ev(long seq) {
        return new LiveEvent(seq, "X", "p" + seq, Instant.EPOCH);
    }

    @Test
    void deliversEnqueuedEventsInOrder() {
        RecordingSink sink = new RecordingSink();
        Subscriber sub = new Subscriber(sink, 8, INLINE);

        sub.enqueue(ev(0));
        sub.enqueue(ev(1));

        assertThat(sink.sent).extracting(LiveEvent::seq).containsExactly(0L, 1L);
        assertThat(sub.isOpen()).isTrue();
    }

    @Test
    void overflowDisconnectsTheSubscriber() {
        RecordingSink sink = new RecordingSink();
        // Capacity 1, and a sender that never drains, so the queue fills then overflows.
        Executor noDrain = task -> { /* drop the drain task */ };
        Subscriber sub = new Subscriber(sink, 1, noDrain);

        sub.enqueue(ev(0)); // fills the queue
        sub.enqueue(ev(1)); // overflow -> disconnect

        assertThat(sub.isOpen()).isFalse();
        assertThat(sink.completed).isTrue();
    }

    @Test
    void sendFailureClosesTheSubscriber() {
        RecordingSink sink = new RecordingSink();
        sink.failWith = new java.io.IOException("client gone");
        Subscriber sub = new Subscriber(sink, 8, INLINE);

        sub.enqueue(ev(0));

        assertThat(sub.isOpen()).isFalse();
        assertThat(sink.completed).isTrue();
    }
}
