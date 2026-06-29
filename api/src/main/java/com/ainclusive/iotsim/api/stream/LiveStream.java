package com.ainclusive.iotsim.api.stream;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Supplier;

/**
 * One stream (one {@link StreamKey}). Holds a bounded ring buffer of recent events
 * for {@code Last-Event-ID} replay and a set of subscribers. {@code publish} and
 * {@code addSubscriber} mutate the buffer under one lock so a joining subscriber's
 * replay and its registration for live events are atomic — no event is duplicated
 * or lost across the seam.
 */
final class LiveStream {

    /** Event type telling the client to refetch history then resume live. */
    static final String RESYNC = "resync";

    private final int bufferCapacity;
    private final Object lock = new Object();
    private final Deque<LiveEvent> buffer = new ArrayDeque<>();
    private final Set<Subscriber> subscribers = new CopyOnWriteArraySet<>();
    private long nextSeq = 0;

    LiveStream(int bufferCapacity) {
        this.bufferCapacity = bufferCapacity;
    }

    LiveEvent publish(String type, Object data, Instant at) {
        synchronized (lock) {
            LiveEvent event = new LiveEvent(nextSeq++, type, data, at);
            buffer.addLast(event);
            if (buffer.size() > bufferCapacity) {
                buffer.removeFirst();
            }
            for (Subscriber sub : subscribers) {
                sub.enqueue(event);
            }
            return event;
        }
    }

    void addSubscriber(Subscriber sub, String lastEventId) {
        addSubscriber(sub, lastEventId, List.of());
    }

    void addSubscriber(Subscriber sub, String lastEventId, List<LiveEvent> initial) {
        addSubscriber(sub, lastEventId, () -> initial);
    }

    /**
     * Registers {@code sub} for live events and seeds it with a snapshot computed by
     * {@code initialSupplier} <em>under the lock</em>. Computing the snapshot inside the
     * lock closes the gap between "read the snapshot" and "start receiving live events":
     * no {@code publish} can interleave, so any event published after this subscriber
     * joins is delivered live and none is lost (matters when {@code Last-Event-ID} replay
     * is disabled, as for the clients stream). The snapshot is still enqueued first, so
     * the subscriber sees it before any live event.
     */
    void addSubscriber(Subscriber sub, String lastEventId, Supplier<List<LiveEvent>> initialSupplier) {
        synchronized (lock) {
            for (LiveEvent e : initialSupplier.get()) {
                sub.enqueue(e);
            }
            for (LiveEvent replay : backlogFor(lastEventId)) {
                sub.enqueue(replay);
            }
            subscribers.add(sub);
        }
    }

    void removeSubscriber(Subscriber sub) {
        subscribers.remove(sub);
    }

    /** Sends an unbuffered event (heartbeat/resync) to current subscribers. */
    void broadcast(LiveEvent event) {
        synchronized (lock) {
            for (Subscriber sub : subscribers) {
                sub.enqueue(event);
            }
        }
    }

    /** Closes and drops every subscriber (registry shutdown). */
    void closeAll() {
        synchronized (lock) {
            for (Subscriber sub : subscribers) {
                sub.close();
            }
            subscribers.clear();
        }
    }

    int subscriberCount() {
        return subscribers.size();
    }

    /** Events to send before going live; called under {@code lock}. */
    private List<LiveEvent> backlogFor(String lastEventId) {
        if (lastEventId == null) {
            return List.of();
        }
        long lid;
        try {
            lid = Long.parseLong(lastEventId.trim());
        } catch (NumberFormatException e) {
            return List.of(resync());
        }
        long latest = nextSeq - 1;
        if (lid == latest) {
            return List.of();
        }
        long oldest = buffer.isEmpty() ? latest + 1 : buffer.peekFirst().seq();
        if (lid > latest || lid < oldest - 1) {
            return List.of(resync());
        }
        List<LiveEvent> tail = new ArrayList<>();
        for (LiveEvent e : buffer) {
            if (e.seq() > lid) {
                tail.add(e);
            }
        }
        return tail;
    }

    private static LiveEvent resync() {
        return new LiveEvent(LiveEvent.NO_SEQ, RESYNC, Map.of("reason", "gap"), Instant.now());
    }
}
