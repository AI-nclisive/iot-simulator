package com.ainclusive.iotsim.protocolmodel;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;
import java.util.random.RandomGeneratorFactory;

/**
 * The reproducibility inputs of a run — the "deterministic run settings" of
 * {@code SPEC.md} "Run Deterministic Scenarios", built on the determinism
 * foundation in {@code backend-specs/01_PROTOCOL_NEUTRAL_MODEL.md} §4.
 *
 * <p>A run is fully determined by its {@code seed} (feeds the {@link SeededRng})
 * and its {@code startTime} (feeds the {@link MutableClock}): the same settings +
 * same schema + same scenario yield an identical value sequence and event order.
 * {@link #newContext()} materializes these into a fresh {@link DeterminismContext}
 * for one run. The clock runs in UTC — the neutral model's {@code sourceTime} is the
 * UTC ordering key and is what generators read; a local zone would not affect it.
 *
 * <p>Even an ad-hoc "run now" should be replayable, so {@link #withRandomSeed} picks
 * a seed from system entropy and <em>captures</em> it here — re-running from the
 * captured seed reproduces the run.
 *
 * @param seed      master seed for the run's RNG streams
 * @param startTime logical instant the run's clock starts at (UTC ordering key)
 */
public record DeterministicSettings(long seed, Instant startTime) {

    public DeterministicSettings {
        Objects.requireNonNull(startTime, "startTime");
    }

    /** Settings with an entropy-chosen (but captured, hence replayable) seed. */
    public static DeterministicSettings withRandomSeed(Instant startTime) {
        return new DeterministicSettings(randomSeed(), startTime);
    }

    /** Materializes a fresh, run-scoped {@link DeterminismContext} from these settings. */
    public DeterminismContext newContext() {
        return new DeterminismContext(MutableClock.at(startTime, ZoneOffset.UTC), SeededRng.withSeed(seed));
    }

    private static long randomSeed() {
        return RandomGeneratorFactory.getDefault().create().nextLong();
    }
}
