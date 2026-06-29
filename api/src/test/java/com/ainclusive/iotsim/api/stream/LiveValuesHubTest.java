package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class LiveValuesHubTest {

    record Published(StreamKey key, String type, Object data) {}

    static final class RecordingPublisher implements LiveEventPublisher {
        final List<Published> events = new CopyOnWriteArrayList<>();
        @Override
        public void publish(StreamKey key, String type, Object data, Instant at) {
            events.add(new Published(key, type, data));
        }
    }

    private static NeutralValue v(String node, int val) {
        return NeutralValue.good(node, Instant.EPOCH, val);
    }

    @Test
    void flushPublishesChangedValuesPerSource() {
        RecordingPublisher pub = new RecordingPublisher();
        LiveValueStore store = new LiveValueStore();
        LiveValuesHub hub = new LiveValuesHub(pub, store);

        hub.onValues("d1", List.of(v("n1", 1)), Instant.EPOCH);
        hub.flushTick();

        assertThat(pub.events).singleElement().satisfies(p -> {
            assertThat(p.key()).isEqualTo(StreamKey.values("d1"));
            assertThat(p.type()).isEqualTo("values");
            assertThat((List<?>) p.data()).hasSize(1);
            assertThat(((StreamValue) ((List<?>) p.data()).get(0)).nodeId()).isEqualTo("n1");
        });
    }

    @Test
    void flushPublishesNothingWhenNoChanges() {
        RecordingPublisher pub = new RecordingPublisher();
        LiveValuesHub hub = new LiveValuesHub(pub, new LiveValueStore());
        hub.flushTick();
        assertThat(pub.events).isEmpty();
    }
}
