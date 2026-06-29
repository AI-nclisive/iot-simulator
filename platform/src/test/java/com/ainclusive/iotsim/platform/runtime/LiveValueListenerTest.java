package com.ainclusive.iotsim.platform.runtime;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

class LiveValueListenerTest {

    @Test
    void noneIgnoresEventsWithoutThrowing() {
        assertThatCode(() -> LiveValueListener.NONE.onValues(
                "d1", List.of(NeutralValue.good("n1", Instant.EPOCH, 1)), Instant.EPOCH))
            .doesNotThrowAnyException();
    }
}
