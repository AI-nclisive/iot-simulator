package com.ainclusive.iotsim.protocolmodel;

import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DeterminismContextTest {

    @Test
    void rejectsNullClockOrRng() {
        MutableClock clock = MutableClock.at(Instant.EPOCH, ZoneOffset.UTC);
        SeededRng rng = SeededRng.withSeed(1L);
        assertThatNullPointerException().isThrownBy(() -> new DeterminismContext(null, rng));
        assertThatNullPointerException().isThrownBy(() -> new DeterminismContext(clock, null));
    }
}
