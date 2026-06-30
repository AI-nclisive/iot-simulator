package com.ainclusive.iotsim.protocolmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.random.RandomGenerator;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class DeterministicSettingsTest {

    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void keepsSeedAndStartTime() {
        DeterministicSettings settings = new DeterministicSettings(42L, START);
        assertThat(settings.seed()).isEqualTo(42L);
        assertThat(settings.startTime()).isEqualTo(START);
    }

    @Test
    void rejectsNullStartTime() {
        assertThatNullPointerException().isThrownBy(() -> new DeterministicSettings(1L, null));
    }

    @Test
    void contextClockStartsAtStartTimeInUtc() {
        DeterminismContext ctx = new DeterministicSettings(42L, START).newContext();
        assertThat(ctx.clock().instant()).isEqualTo(START);
        assertThat(ctx.clock().getZone()).isEqualTo(ZoneOffset.UTC);
    }

    /** The whole point: identical settings fully determine the run's values. */
    @Test
    void sameSettingsReproduceClockAndRngAcrossContexts() {
        DeterministicSettings settings = new DeterministicSettings(42L, START);
        DeterminismContext a = settings.newContext();
        DeterminismContext b = settings.newContext();

        assertThat(a.clock().instant()).isEqualTo(b.clock().instant());
        assertThat(draw(a.rng().stream("temp"), 16)).containsExactly(draw(b.rng().stream("temp"), 16));
    }

    @Test
    void eachContextOwnsAnIndependentClock() {
        DeterministicSettings settings = new DeterministicSettings(42L, START);
        DeterminismContext a = settings.newContext();
        DeterminismContext b = settings.newContext();

        a.clock().advance(Duration.ofHours(1));

        assertThat(b.clock().instant()).isEqualTo(START);
    }

    @Test
    void randomSeedSettingsAreThemselvesReproducible() {
        DeterministicSettings captured = DeterministicSettings.withRandomSeed(START);
        // re-running from the captured seed reproduces the sequence
        DeterministicSettings replay = new DeterministicSettings(captured.seed(), START);
        assertThat(draw(captured.newContext().rng().stream("temp"), 16))
                .containsExactly(draw(replay.newContext().rng().stream("temp"), 16));
    }

    @Test
    void randomSeedIsNotConstant() {
        long distinctSeeds = LongStream.range(0, 8)
                .map(i -> DeterministicSettings.withRandomSeed(START).seed())
                .distinct()
                .count();
        assertThat(distinctSeeds).isGreaterThan(1);
    }

    private static long[] draw(RandomGenerator g, int n) {
        long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = g.nextLong();
        }
        return out;
    }
}
