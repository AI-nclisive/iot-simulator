package com.ainclusive.iotsim.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositeRuntimeActivityListenerTest {

    @Test
    void forwardsEachEventToEveryDelegateInOrder() {
        List<String> log = new ArrayList<>();
        RuntimeActivityListener a = e -> log.add("a:" + e.type());
        RuntimeActivityListener b = e -> log.add("b:" + e.type());
        CompositeRuntimeActivityListener composite = new CompositeRuntimeActivityListener(a, b);

        composite.onRuntimeActivity(new RuntimeActivityEvent("d1", "SOURCE_START", Instant.EPOCH, null));

        assertThat(log).containsExactly("a:SOURCE_START", "b:SOURCE_START");
    }

    @Test
    void toleratesNoDelegates() {
        CompositeRuntimeActivityListener composite = new CompositeRuntimeActivityListener();
        assertThatCode(() ->
                composite.onRuntimeActivity(new RuntimeActivityEvent("d1", "X", Instant.EPOCH, null)))
            .doesNotThrowAnyException();
    }
}
