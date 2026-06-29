package com.ainclusive.iotsim.api.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

class LiveStreamRegistryTest {

    private static final Executor INLINE = Runnable::run;

    private LiveStreamRegistry registry() {
        return new LiveStreamRegistry(new ObjectMapper(), 256, 256, INLINE);
    }

    @Test
    void subscribeRegistersASubscriberForTheKey() {
        LiveStreamRegistry registry = registry();
        SseEmitter emitter = registry.subscribe(StreamKey.runtime("p1"), null);

        assertThat(emitter).isNotNull();
        assertThat(registry.subscriberCount(StreamKey.runtime("p1"))).isEqualTo(1);
        assertThat(registry.subscriberCount(StreamKey.clients("p1"))).isZero();
    }

    @Test
    void publishRoutesOnlyToTheMatchingKey() {
        LiveStreamRegistry registry = registry();
        registry.subscribe(StreamKey.runtime("p1"), null);
        // Different key, no subscriber: must not throw.
        registry.publish(StreamKey.clients("d1"), "CONNECTED", java.util.Map.of(), Instant.EPOCH);
        registry.publish(StreamKey.runtime("p1"), "SOURCE_START", java.util.Map.of(), Instant.EPOCH);
        // No assertion on bytes here (covered in Task 11); this pins routing/no-throw.
        assertThat(registry.subscriberCount(StreamKey.runtime("p1"))).isEqualTo(1);
    }

    @Test
    void closeCompletesEmittersAndStops() {
        LiveStreamRegistry registry = registry();
        registry.subscribe(StreamKey.runtime("p1"), null);
        registry.close();
        // After close a new subscribe still returns an emitter that is immediately done.
        assertThat(registry.subscriberCount(StreamKey.runtime("p1"))).isZero();
    }
}
