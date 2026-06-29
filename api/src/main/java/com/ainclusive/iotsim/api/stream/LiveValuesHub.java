package com.ainclusive.iotsim.api.stream;

import com.ainclusive.iotsim.platform.runtime.LiveValueListener;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Bridges the supervisor's value tee (IS-051) to the live SSE streams: records values
 * into a {@link LiveValueStore} (conflation) and, on a fixed cadence, publishes the
 * changed values per source as {@code values} deltas. {@code onValues} runs on the
 * supervisor/replay thread, so it only touches the store; sending happens on the
 * registry's pool via {@link LiveEventPublisher}.
 */
@Component
public final class LiveValuesHub implements LiveValueListener, AutoCloseable {

    private static final int FLUSH_MILLIS = 250;

    private final LiveEventPublisher publisher;
    private final LiveValueStore store;
    private final ScheduledExecutorService flusher; // null in test ctor

    @Autowired
    public LiveValuesHub(LiveEventPublisher publisher) {
        this.publisher = publisher;
        this.store = new LiveValueStore();
        this.flusher = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sse-values-flush");
            t.setDaemon(true);
            return t;
        });
        this.flusher.scheduleAtFixedRate(
                this::flushTick, FLUSH_MILLIS, FLUSH_MILLIS, TimeUnit.MILLISECONDS);
    }

    /** Test ctor: caller-driven {@link #flushTick()}, no scheduler thread. */
    LiveValuesHub(LiveEventPublisher publisher, LiveValueStore store) {
        this.publisher = publisher;
        this.store = store;
        this.flusher = null;
    }

    LiveValueStore store() {
        return store;
    }

    @Override
    public void onValues(String dataSourceId, List<NeutralValue> values, Instant at) {
        store.record(dataSourceId, values);
    }

    void flushTick() {
        for (String dataSourceId : store.dirtySources()) {
            List<NeutralValue> changed = store.drainChanged(dataSourceId);
            if (!changed.isEmpty()) {
                List<StreamValue> payload = changed.stream().map(StreamValue::from).toList();
                publisher.publish(StreamKey.values(dataSourceId), "values", payload, Instant.now());
            }
        }
    }

    @Override
    public void close() {
        if (flusher != null) {
            flusher.shutdownNow();
        }
    }
}
