package com.ainclusive.iotsim.platform.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.platform.runtime.ClientActivityEvent.Kind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CompositeClientActivityListenerTest {

    @Test
    void fansEventToEveryDelegateInOrder() {
        List<String> calls = new ArrayList<>();
        ClientActivityListener first = e -> calls.add("first");
        ClientActivityListener second = e -> calls.add("second");
        CompositeClientActivityListener composite = new CompositeClientActivityListener(first, second);

        composite.onClientActivity(new ClientActivityEvent("ds-1", Kind.CONNECTED, "c-1", Instant.EPOCH));

        assertThat(calls).containsExactly("first", "second");
    }

    @Test
    void deliversTheSameEventToDelegates() {
        List<ClientActivityEvent> seen = new ArrayList<>();
        CompositeClientActivityListener composite =
                new CompositeClientActivityListener(seen::add);
        ClientActivityEvent event = new ClientActivityEvent("ds-1", Kind.DISCONNECTED, "c-1", Instant.EPOCH);

        composite.onClientActivity(event);

        assertThat(seen).containsExactly(event);
    }
}
