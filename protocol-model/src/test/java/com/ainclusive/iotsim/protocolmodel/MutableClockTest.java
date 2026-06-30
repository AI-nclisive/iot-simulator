package com.ainclusive.iotsim.protocolmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class MutableClockTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void reportsConfiguredInstantAndZone() {
        MutableClock clock = MutableClock.at(START, ZoneOffset.UTC);
        assertThat(clock.instant()).isEqualTo(START);
        assertThat(clock.getZone()).isEqualTo(ZoneOffset.UTC);
    }

    @Test
    void advanceMovesInstantForward() {
        MutableClock clock = MutableClock.at(START, ZoneOffset.UTC);
        clock.advance(Duration.ofSeconds(90));
        assertThat(clock.instant()).isEqualTo(START.plusSeconds(90));
    }

    @Test
    void setInstantResetsTime() {
        MutableClock clock = MutableClock.at(START, ZoneOffset.UTC);
        clock.advance(Duration.ofHours(5));
        clock.setInstant(START);
        assertThat(clock.instant()).isEqualTo(START);
    }

    @Test
    void rejectsNegativeAdvance() {
        MutableClock clock = MutableClock.at(START, ZoneOffset.UTC);
        assertThatIllegalArgumentException().isThrownBy(() -> clock.advance(Duration.ofSeconds(-1)));
    }

    @Test
    void withZoneSharesOngoingTime() {
        MutableClock clock = MutableClock.at(START, ZoneOffset.UTC);
        var tokyo = clock.withZone(ZoneId.of("Asia/Tokyo"));
        clock.advance(Duration.ofMinutes(10));
        assertThat(tokyo.getZone()).isEqualTo(ZoneId.of("Asia/Tokyo"));
        assertThat(tokyo.instant()).isEqualTo(START.plusSeconds(600));
    }

    @Test
    void rejectsNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> MutableClock.at(null, ZoneOffset.UTC));
        assertThatNullPointerException().isThrownBy(() -> MutableClock.at(START, null));
    }
}
