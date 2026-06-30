package com.ainclusive.iotsim.protocolmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class SeededRngTest {

    @Test
    void exposesItsSeed() {
        assertThat(SeededRng.withSeed(42L).seed()).isEqualTo(42L);
    }

    @Test
    void sameSeedAndStreamProduceIdenticalSequence() {
        long[] a = draw(SeededRng.withSeed(42L).stream("temp"), 16);
        long[] b = draw(SeededRng.withSeed(42L).stream("temp"), 16);
        assertThat(b).containsExactly(a);
    }

    @Test
    void repeatedCallsForSameNameAreIdentical() {
        SeededRng rng = SeededRng.withSeed(7L);
        assertThat(draw(rng.stream("temp"), 16)).containsExactly(draw(rng.stream("temp"), 16));
    }

    @Test
    void differentStreamsProduceDifferentSequences() {
        SeededRng rng = SeededRng.withSeed(42L);
        assertThat(draw(rng.stream("temp"), 16)).isNotEqualTo(draw(rng.stream("pressure"), 16));
    }

    @Test
    void differentSeedsProduceDifferentSequences() {
        long[] a = draw(SeededRng.withSeed(1L).stream("temp"), 16);
        long[] b = draw(SeededRng.withSeed(2L).stream("temp"), 16);
        assertThat(a).isNotEqualTo(b);
    }

    /**
     * The load-bearing property (spec 01 §4): a named stream's sequence must not
     * depend on whether or how other streams were created/consumed first — so
     * adding or removing a variable can never perturb another variable's values.
     */
    @Test
    void namedStreamIsStableRegardlessOfOtherStreamUsage() {
        SeededRng isolated = SeededRng.withSeed(42L);
        long[] expected = draw(isolated.stream("pressure"), 16);

        SeededRng perturbed = SeededRng.withSeed(42L);
        draw(perturbed.stream("temp"), 5); // create + consume an unrelated stream first
        perturbed.stream("humidity");
        long[] actual = draw(perturbed.stream("pressure"), 16);

        assertThat(actual).containsExactly(expected);
    }

    @Test
    void rejectsNullSeedlessStreamName() {
        assertThatNullPointerException().isThrownBy(() -> SeededRng.withSeed(1L).stream(null));
    }

    private static long[] draw(RandomGenerator g, int n) {
        long[] out = new long[n];
        for (int i = 0; i < n; i++) {
            out[i] = g.nextLong();
        }
        return out;
    }
}
