package com.ainclusive.iotsim.domain.synthetic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.Constant;
import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.EnumCycle;
import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.Ramp;
import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.RandomUniform;
import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.RandomWalk;
import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.Signal;
import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.Sine;
import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.Square;
import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.StepSequence;
import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.StepSequence.Step;
import com.ainclusive.iotsim.protocolmodel.SeededRng;
import java.time.Duration;
import java.util.List;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class SyntheticPatternTest {

    private static RandomGenerator rng() {
        return SeededRng.withSeed(42L).stream("n");
    }

    private static double num(Object value) {
        return ((Number) value).doubleValue();
    }

    @Test
    void constantAlwaysReturnsItsValue() {
        Signal sig = new Constant(7.5).bind(rng());
        assertThat(num(sig.valueAt(0, Duration.ZERO))).isEqualTo(7.5);
        assertThat(num(sig.valueAt(99, Duration.ofSeconds(99)))).isEqualTo(7.5);
    }

    @Test
    void rampSawtoothsFromMinTowardMaxOverPeriod() {
        Signal sig = new Ramp(0, 10, Duration.ofSeconds(10)).bind(rng());
        assertThat(num(sig.valueAt(0, Duration.ZERO))).isEqualTo(0.0, within(1e-9));
        assertThat(num(sig.valueAt(0, Duration.ofSeconds(5)))).isEqualTo(5.0, within(1e-9));
        assertThat(num(sig.valueAt(0, Duration.ofSeconds(10)))).isEqualTo(0.0, within(1e-9)); // wraps
    }

    @Test
    void sineStartsAtMidPeaksAtQuarterTroughAtThreeQuarter() {
        Signal sig = new Sine(0, 10, Duration.ofSeconds(8)).bind(rng());
        assertThat(num(sig.valueAt(0, Duration.ZERO))).isEqualTo(5.0, within(1e-9));
        assertThat(num(sig.valueAt(0, Duration.ofSeconds(2)))).isEqualTo(10.0, within(1e-9));
        assertThat(num(sig.valueAt(0, Duration.ofSeconds(6)))).isEqualTo(0.0, within(1e-9));
    }

    @Test
    void squareIsHighInFirstHalfLowInSecondHalf() {
        Signal sig = new Square(1, 5, Duration.ofSeconds(10)).bind(rng());
        assertThat(num(sig.valueAt(0, Duration.ofSeconds(2)))).isEqualTo(5.0);
        assertThat(num(sig.valueAt(0, Duration.ofSeconds(7)))).isEqualTo(1.0);
    }

    @Test
    void randomUniformStaysWithinRange() {
        Signal sig = new RandomUniform(-1, 1).bind(rng());
        for (long i = 0; i < 1000; i++) {
            assertThat(num(sig.valueAt(i, Duration.ofSeconds(i)))).isBetween(-1.0, 1.0);
        }
    }

    @Test
    void randomWalkStaysWithinBounds() {
        Signal sig = new RandomWalk(0, 10, 2.0).bind(rng());
        for (long i = 0; i < 1000; i++) {
            assertThat(num(sig.valueAt(i, Duration.ofSeconds(i)))).isBetween(0.0, 10.0);
        }
    }

    @Test
    void randomWalkIsReproducibleForSameSeed() {
        Signal a = new RandomWalk(0, 10, 2.0).bind(SeededRng.withSeed(1L).stream("n"));
        Signal b = new RandomWalk(0, 10, 2.0).bind(SeededRng.withSeed(1L).stream("n"));
        for (long i = 0; i < 50; i++) {
            assertThat(a.valueAt(i, Duration.ofSeconds(i))).isEqualTo(b.valueAt(i, Duration.ofSeconds(i)));
        }
    }

    @Test
    void enumCycleCyclesThroughValues() {
        Signal sig = new EnumCycle(List.of("A", "B", "C")).bind(rng());
        assertThat(sig.valueAt(0, Duration.ZERO)).isEqualTo("A");
        assertThat(sig.valueAt(1, Duration.ZERO)).isEqualTo("B");
        assertThat(sig.valueAt(2, Duration.ZERO)).isEqualTo("C");
        assertThat(sig.valueAt(3, Duration.ZERO)).isEqualTo("A");
    }

    @Test
    void stepSequenceHoldsEachValueThenCycles() {
        Signal sig = new StepSequence(List.of(
                        new Step("idle", Duration.ofSeconds(5)),
                        new Step("run", Duration.ofSeconds(3))))
                .bind(rng());
        assertThat(sig.valueAt(0, Duration.ofSeconds(0))).isEqualTo("idle");
        assertThat(sig.valueAt(0, Duration.ofSeconds(4))).isEqualTo("idle");
        assertThat(sig.valueAt(0, Duration.ofSeconds(5))).isEqualTo("run");
        assertThat(sig.valueAt(0, Duration.ofSeconds(7))).isEqualTo("run");
        assertThat(sig.valueAt(0, Duration.ofSeconds(8))).isEqualTo("idle"); // total 8s, cycles
    }

    @Test
    void rejectsInvalidParams() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Sine(10, 0, Duration.ofSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new Sine(0, 10, Duration.ZERO));
        assertThatIllegalArgumentException().isThrownBy(() -> new Ramp(0, 10, Duration.ofSeconds(-1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new RandomWalk(0, 10, -1));
        assertThatIllegalArgumentException().isThrownBy(() -> new EnumCycle(List.of()));
        assertThatIllegalArgumentException().isThrownBy(() -> new StepSequence(List.of()));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Step("x", Duration.ZERO));
    }

    @Test
    void constantAcceptsNonNumericPayloads() {
        // IS-168: a Constant may carry any non-null value a node's data type can be
        // coerced from — not just a number.
        assertThat(new Constant("ns=2;s=Foo").bind(rng()).valueAt(0, Duration.ZERO)).isEqualTo("ns=2;s=Foo");
        byte[] bytes = {1, 2, 3};
        assertThat(new Constant(bytes).bind(rng()).valueAt(0, Duration.ZERO)).isEqualTo(bytes);
        assertThatNullPointerException().isThrownBy(() -> new Constant(null));
    }

    @Test
    void rejectsNonFiniteParams() {
        assertThatIllegalArgumentException().isThrownBy(() -> new Constant(Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() -> new Constant(Double.POSITIVE_INFINITY));
        assertThatIllegalArgumentException().isThrownBy(() -> new Sine(Double.NaN, 10, Duration.ofSeconds(1)));
        assertThatIllegalArgumentException().isThrownBy(() -> new RandomUniform(0, Double.NaN));
        assertThatIllegalArgumentException().isThrownBy(() -> new RandomWalk(0, 10, Double.NaN));
    }
}
