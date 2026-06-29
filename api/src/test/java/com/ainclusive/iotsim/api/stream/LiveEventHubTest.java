package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.platform.runtime.ClientActivityEvent;
import com.ainclusive.iotsim.platform.runtime.RuntimeActivityEvent;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LiveEventHubTest {

    record Published(StreamKey key, String type, Object data) {}

    static final class RecordingPublisher implements LiveEventPublisher {
        final List<Published> events = new CopyOnWriteArrayList<>();

        @Override
        public void publish(StreamKey key, String type, Object data, Instant at) {
            events.add(new Published(key, type, data));
        }
    }

    private static final Executor INLINE = Runnable::run;

    @Test
    void runtimeEventRoutesToProjectStream() {
        RecordingPublisher pub = new RecordingPublisher();
        LiveEventHub hub = new LiveEventHub(pub, ds -> Optional.of("proj-" + ds), INLINE);

        hub.onRuntimeActivity(new RuntimeActivityEvent("d1", "SOURCE_START", Instant.EPOCH, "ok"));

        assertThat(pub.events).singleElement().satisfies(p -> {
            assertThat(p.key()).isEqualTo(StreamKey.runtime("proj-d1"));
            assertThat(p.type()).isEqualTo("SOURCE_START");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) p.data();
            assertThat(data).containsEntry("dataSourceId", "d1")
                    .containsEntry("detail", "ok");
        });
    }

    @Test
    void runtimeEventForUnknownSourceIsDropped() {
        RecordingPublisher pub = new RecordingPublisher();
        LiveEventHub hub = new LiveEventHub(pub, ds -> Optional.empty(), INLINE);

        hub.onRuntimeActivity(new RuntimeActivityEvent("d1", "ERROR", Instant.EPOCH, null));

        assertThat(pub.events).isEmpty();
    }

    @Test
    void clientEventRoutesToDataSourceStreamWithoutResolution() {
        RecordingPublisher pub = new RecordingPublisher();
        AtomicInteger resolverCalls = new AtomicInteger();
        LiveEventHub hub = new LiveEventHub(pub, ds -> {
            resolverCalls.incrementAndGet();
            return Optional.of("p");
        }, INLINE);

        hub.onClientActivity(new ClientActivityEvent(
                "d1", ClientActivityEvent.Kind.CONNECTED, "c7", Instant.EPOCH));

        assertThat(resolverCalls.get()).isZero();
        assertThat(pub.events).singleElement().satisfies(p -> {
            assertThat(p.key()).isEqualTo(StreamKey.clients("d1"));
            assertThat(p.type()).isEqualTo("CONNECTED");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) p.data();
            assertThat(data).containsEntry("clientId", "c7");
        });
    }

    @Test
    void projectResolutionIsCachedPerDataSource() {
        RecordingPublisher pub = new RecordingPublisher();
        AtomicInteger resolverCalls = new AtomicInteger();
        LiveEventHub hub = new LiveEventHub(pub, ds -> {
            resolverCalls.incrementAndGet();
            return Optional.of("p");
        }, INLINE);

        hub.onRuntimeActivity(new RuntimeActivityEvent("d1", "A", Instant.EPOCH, null));
        hub.onRuntimeActivity(new RuntimeActivityEvent("d1", "B", Instant.EPOCH, null));

        assertThat(resolverCalls.get()).isEqualTo(1);
        assertThat(pub.events).hasSize(2);
    }

    @Test
    void listenerHandsOffToDispatchExecutorNeverInline() {
        RecordingPublisher pub = new RecordingPublisher();
        Deque<Runnable> deferred = new ArrayDeque<>();
        LiveEventHub hub = new LiveEventHub(pub, ds -> Optional.of("p"), deferred::add);

        hub.onRuntimeActivity(new RuntimeActivityEvent("d1", "A", Instant.EPOCH, null));

        assertThat(pub.events).isEmpty(); // nothing published on the calling (IPC) thread
        deferred.poll().run();
        assertThat(pub.events).hasSize(1);
    }
}
