package com.ainclusive.iotsim.api.stream;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

/**
 * Owns the live streams keyed by {@link StreamKey}: routes publishes, creates SSE
 * subscriptions, runs the heartbeat, and shuts everything down. Sends never run on
 * the caller's thread — each subscriber drains onto the shared {@code sender} pool.
 */
@Component
public final class LiveStreamRegistry
        implements LiveEventPublisher, LiveStreamSubscriptions, AutoCloseable {

    static final String HEARTBEAT = "heartbeat";

    private static final int DEFAULT_BUFFER = 256;
    private static final int DEFAULT_QUEUE = 256;
    private static final int HEARTBEAT_SECONDS = 15;

    private final ObjectMapper json;
    private final int bufferCapacity;
    private final int queueCapacity;
    private final Executor sender;
    private final ExecutorService ownedSender;          // null in test ctor
    private final ScheduledExecutorService heartbeat;   // null in test ctor
    private final Map<StreamKey, LiveStream> streams = new ConcurrentHashMap<>();

    public LiveStreamRegistry(ObjectMapper json) {
        this.json = json;
        this.bufferCapacity = DEFAULT_BUFFER;
        this.queueCapacity = DEFAULT_QUEUE;
        AtomicInteger n = new AtomicInteger();
        this.ownedSender = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "sse-sender-" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        this.sender = ownedSender;
        this.heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-heartbeat");
            t.setDaemon(true);
            return t;
        });
        this.heartbeat.scheduleAtFixedRate(
                this::heartbeatTick, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    /** Test constructor: explicit sizes, caller-supplied executor, no heartbeat thread. */
    LiveStreamRegistry(ObjectMapper json, int bufferCapacity, int queueCapacity, Executor sender) {
        this.json = json;
        this.bufferCapacity = bufferCapacity;
        this.queueCapacity = queueCapacity;
        this.sender = sender;
        this.ownedSender = null;
        this.heartbeat = null;
    }

    @Override
    public void publish(StreamKey key, String type, Object data, Instant at) {
        LiveStream stream = streams.get(key);
        if (stream != null) {
            stream.publish(type, data, at);
        }
    }

    @Override
    public SseEmitter subscribe(StreamKey key, String lastEventId) {
        LiveStream stream = streams.computeIfAbsent(key, k -> new LiveStream(bufferCapacity));
        SseEmitter emitter = new SseEmitter(0L); // no server-side timeout
        Subscriber sub = new Subscriber(new SseEmitterSink(emitter, json), queueCapacity, sender);
        Runnable remove = () -> {
            stream.removeSubscriber(sub);
            sub.close();
            streams.computeIfPresent(key, (k, s) -> s.subscriberCount() == 0 ? null : s);
        };
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        stream.addSubscriber(sub, lastEventId);
        return emitter;
    }

    void heartbeatTick() {
        LiveEvent beat = new LiveEvent(LiveEvent.NO_SEQ, HEARTBEAT, Map.of(), Instant.now());
        for (LiveStream stream : streams.values()) {
            stream.broadcast(beat);
        }
    }

    int subscriberCount(StreamKey key) {
        LiveStream stream = streams.get(key);
        return stream == null ? 0 : stream.subscriberCount();
    }

    @Override
    public void close() {
        streams.values().forEach(LiveStream::closeAll);
        streams.clear();
        if (heartbeat != null) {
            heartbeat.shutdownNow();
        }
        if (ownedSender != null) {
            ownedSender.shutdown();
        }
    }
}
