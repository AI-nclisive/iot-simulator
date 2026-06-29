package com.ainclusive.iotsim.api.stream;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One live connection. Events are offered to a bounded queue and drained serially
 * onto a shared sender executor (at most one drain task per subscriber at a time,
 * so ordering holds without a thread per connection). A full queue means the
 * client cannot keep up: we close it (backpressure = disconnect); it will
 * reconnect with {@code Last-Event-ID}. A failed {@code send} closes it too.
 */
final class Subscriber {

    private final EventSink sink;
    private final BlockingQueue<LiveEvent> queue;
    private final Executor sender;
    private final AtomicBoolean draining = new AtomicBoolean(false);
    private final AtomicBoolean open = new AtomicBoolean(true);

    Subscriber(EventSink sink, int queueCapacity, Executor sender) {
        this.sink = sink;
        this.queue = new ArrayBlockingQueue<>(queueCapacity);
        this.sender = sender;
    }

    void enqueue(LiveEvent event) {
        if (!open.get()) {
            return;
        }
        if (!queue.offer(event)) {
            close(); // overflow: slow client, disconnect
            return;
        }
        scheduleDrain();
    }

    boolean isOpen() {
        return open.get();
    }

    void close() {
        if (open.compareAndSet(true, false)) {
            sink.complete();
        }
    }

    private void scheduleDrain() {
        if (draining.compareAndSet(false, true)) {
            sender.execute(this::drain);
        }
    }

    private void drain() {
        try {
            LiveEvent event;
            while (open.get() && (event = queue.poll()) != null) {
                sink.send(event);
            }
        } catch (Exception e) {
            close(); // connection gone or serialization failure
        } finally {
            draining.set(false);
            // An event enqueued between the last poll and clearing the flag must
            // still be drained.
            if (open.get() && !queue.isEmpty()) {
                scheduleDrain();
            }
        }
    }
}
