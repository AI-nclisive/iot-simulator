package com.ainclusive.iotsim.domain.synthetic;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * A synthetic value-generation pattern — the protocol-neutral "shape" of a
 * generated signal, per {@code backend-specs/06_ARTIFACT_FORMATS.md} "Synthetic
 * generation model" and {@code 01 §4} (determinism).
 *
 * <p>A pattern is immutable configuration. {@link #bind(RandomGenerator)} produces a
 * stateful {@link Signal} for one run, bound to one node's RNG stream (from a
 * {@link com.ainclusive.iotsim.protocolmodel.DeterminismContext}); the same seed and
 * pattern therefore yield an identical series. Numeric patterns emit a {@code Double};
 * {@link EnumCycle}, {@link RandomChoice}, and {@link StepSequence} emit their configured element values. The
 * generator coerces the raw value to the node's data type (see
 * {@link SyntheticValueCoercion}).
 */
public sealed interface SyntheticPattern {

    /** A stateful, per-run value source. Called in tick order (0, 1, 2, …). */
    @FunctionalInterface
    interface Signal {
        /**
         * Raw value for a sample.
         *
         * @param tick    zero-based sample index
         * @param elapsed time since the run's start instant for this sample
         */
        Object valueAt(long tick, Duration elapsed);
    }

    /** Binds this pattern to a node's RNG stream, yielding a fresh run-scoped signal. */
    Signal bind(RandomGenerator rng);

    /**
     * A fixed value. {@code value} is usually a {@code Double} (the common numeric
     * case), but may be any non-null payload the node's data type can be coerced
     * from (e.g. a {@code String} for {@code NODE_ID}/{@code GUID}, a {@code byte[]}
     * for {@code BYTES}) — see {@link SyntheticValueCoercion} for the types that
     * only accept {@code Constant} (a dynamic pattern has no physical meaning for
     * structural/identifier fields).
     */
    record Constant(Object value) implements SyntheticPattern {
        public Constant {
            Objects.requireNonNull(value, "value");
            if (value instanceof Double d) {
                requireFinite(d, "value");
            }
        }

        @Override
        public Signal bind(RandomGenerator rng) {
            return (tick, elapsed) -> value;
        }
    }

    /** Linear sawtooth rising from {@code min} to {@code max} over {@code period}, then repeating. */
    record Ramp(double min, double max, Duration period) implements SyntheticPattern {
        public Ramp {
            requireRange(min, max);
            requirePositive(period);
        }

        @Override
        public Signal bind(RandomGenerator rng) {
            return (tick, elapsed) -> min + phase(elapsed, period) * (max - min);
        }
    }

    /** Sine wave oscillating across {@code [min, max]} with the given {@code period}; starts at the midpoint. */
    record Sine(double min, double max, Duration period) implements SyntheticPattern {
        public Sine {
            requireRange(min, max);
            requirePositive(period);
        }

        @Override
        public Signal bind(RandomGenerator rng) {
            double mid = (min + max) / 2.0;
            double amplitude = (max - min) / 2.0;
            return (tick, elapsed) -> mid + amplitude * Math.sin(2 * Math.PI * phase(elapsed, period));
        }
    }

    /** Square wave: {@code max} for the first half of each {@code period}, {@code min} for the second. */
    record Square(double min, double max, Duration period) implements SyntheticPattern {
        public Square {
            requireRange(min, max);
            requirePositive(period);
        }

        @Override
        public Signal bind(RandomGenerator rng) {
            return (tick, elapsed) -> phase(elapsed, period) < 0.5 ? max : min;
        }
    }

    /** Independent uniform draws across {@code [min, max]}. */
    record RandomUniform(double min, double max) implements SyntheticPattern {
        public RandomUniform {
            requireRange(min, max);
        }

        @Override
        public Signal bind(RandomGenerator rng) {
            return (tick, elapsed) -> min + rng.nextDouble() * (max - min);
        }
    }

    /** Gaussian random walk starting at the midpoint, clamped to {@code [min, max]}. */
    record RandomWalk(double min, double max, double volatility) implements SyntheticPattern {
        public RandomWalk {
            requireRange(min, max);
            requireFinite(volatility, "volatility");
            if (volatility < 0) {
                throw new IllegalArgumentException("volatility must be >= 0: " + volatility);
            }
        }

        @Override
        public Signal bind(RandomGenerator rng) {
            double[] current = {(min + max) / 2.0};
            // Stateful: each call advances the walk regardless of tick/elapsed,
            // so it must be sampled strictly in tick order (cannot be seeked).
            return (tick, elapsed) -> {
                double next = current[0] + rng.nextGaussian() * volatility;
                current[0] = Math.clamp(next, min, max);
                return current[0];
            };
        }
    }

    /** Cycles through the given values in order, one per tick. */
    record EnumCycle(List<Object> values) implements SyntheticPattern {
        public EnumCycle {
            values = List.copyOf(Objects.requireNonNull(values, "values"));
            if (values.isEmpty()) {
                throw new IllegalArgumentException("EnumCycle requires at least one value");
            }
        }

        @Override
        public Signal bind(RandomGenerator rng) {
            return (tick, elapsed) -> values.get((int) Math.floorMod(tick, values.size()));
        }
    }

    /** Chooses one configured value independently for each tick, using the run-scoped seeded RNG. */
    record RandomChoice(List<Object> values) implements SyntheticPattern {
        public RandomChoice {
            values = List.copyOf(Objects.requireNonNull(values, "values"));
            if (values.isEmpty()) {
                throw new IllegalArgumentException("RandomChoice requires at least one value");
            }
        }

        @Override
        public Signal bind(RandomGenerator rng) {
            return (tick, elapsed) -> values.get(rng.nextInt(values.size()));
        }
    }

    /** Piecewise-constant sequence: each step holds its value for a duration, then the sequence repeats. */
    record StepSequence(List<Step> steps) implements SyntheticPattern {

        /** One step of a {@link StepSequence}: a value held for {@code hold}. */
        public record Step(Object value, Duration hold) {
            public Step {
                requirePositive(hold);
            }
        }

        public StepSequence {
            steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
            if (steps.isEmpty()) {
                throw new IllegalArgumentException("StepSequence requires at least one step");
            }
        }

        @Override
        public Signal bind(RandomGenerator rng) {
            long totalNanos = steps.stream().mapToLong(s -> s.hold().toNanos()).sum();
            return (tick, elapsed) -> {
                long offset = Math.floorMod(elapsed.toNanos(), totalNanos);
                long acc = 0;
                for (Step step : steps) {
                    acc += step.hold().toNanos();
                    if (offset < acc) {
                        return step.value();
                    }
                }
                // unreachable: offset = floorMod(.., totalNanos) is always < totalNanos = sum(holds)
                throw new AssertionError("step offset " + offset + " >= total " + totalNanos);
            };
        }
    }

    /** Fractional position within {@code period} for {@code elapsed}, in {@code [0, 1)}. */
    private static double phase(Duration elapsed, Duration period) {
        long periodNanos = period.toNanos();
        return Math.floorMod(elapsed.toNanos(), periodNanos) / (double) periodNanos;
    }

    private static void requireRange(double min, double max) {
        requireFinite(min, "min");
        requireFinite(max, "max");
        if (min > max) {
            throw new IllegalArgumentException("min must be <= max: min=" + min + " max=" + max);
        }
    }

    private static void requireFinite(double value, String name) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(name + " must be finite: " + value);
        }
    }

    private static void requirePositive(Duration period) {
        if (period.isZero() || period.isNegative()) {
            throw new IllegalArgumentException("duration must be positive: " + period);
        }
    }
}
