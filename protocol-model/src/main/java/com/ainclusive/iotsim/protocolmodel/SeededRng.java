package com.ainclusive.iotsim.protocolmodel;

import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.random.RandomGenerator;
import java.util.random.RandomGeneratorFactory;

/**
 * Seeded source of independent, reproducible random streams — the RNG half of the
 * determinism foundation in {@code backend-specs/01_PROTOCOL_NEUTRAL_MODEL.md} §4.
 *
 * <p>From a single master seed it derives any number of <em>named</em> streams via
 * {@link #stream(String)}. Each stream is seeded deterministically from the master
 * seed and the name alone, so:
 *
 * <ul>
 *   <li>the same master seed + name always yield the same sequence (reproducible);
 *   <li>a stream's sequence is independent of whether or how other streams were
 *       created or consumed — adding or removing a variable can never perturb
 *       another variable's values. This is the load-bearing guarantee behind
 *       "same seed + same schema ⇒ identical value sequence".
 * </ul>
 *
 * <p>Streams are keyed by a stable identity such as a node {@code nodeId}. The
 * generator algorithm is pinned ({@value #ALGORITHM}, a specified LXM generator) so a
 * seed maps to the same sequence regardless of the platform's default generator.
 */
public final class SeededRng {

    /** Pinned generator algorithm — fixed (not the platform default) so a seed reproduces its sequence. */
    static final String ALGORITHM = "L64X128MixRandom";

    private static final RandomGeneratorFactory<RandomGenerator> FACTORY =
            RandomGeneratorFactory.of(ALGORITHM);

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long FNV_OFFSET = 0xCBF29CE484222325L;
    private static final long FNV_PRIME = 0x100000001B3L;

    private final long seed;

    private SeededRng(long seed) {
        this.seed = seed;
    }

    /** Creates an RNG source rooted at the given master seed. */
    public static SeededRng withSeed(long seed) {
        return new SeededRng(seed);
    }

    /** The master seed this source was created with. */
    public long seed() {
        return seed;
    }

    /**
     * Returns a fresh generator for the named stream, seeded deterministically from
     * the master seed and {@code name}. Repeated calls with the same name yield
     * generators that produce identical sequences.
     *
     * @param name stable stream identity (e.g. a node id); must not be {@code null}
     */
    public RandomGenerator stream(String name) {
        Objects.requireNonNull(name, "name");
        return FACTORY.create(deriveSeed(seed, name));
    }

    private static long deriveSeed(long masterSeed, String name) {
        return mix64(masterSeed ^ (fnv1a64(name) * GOLDEN_GAMMA));
    }

    /** Stable 64-bit FNV-1a hash of the name's UTF-8 bytes (JVM-independent). */
    private static long fnv1a64(String name) {
        long hash = FNV_OFFSET;
        for (byte b : name.getBytes(StandardCharsets.UTF_8)) {
            hash = (hash ^ (b & 0xFF)) * FNV_PRIME;
        }
        return hash;
    }

    /** SplitMix64 finalizer — spreads bits so adjacent seeds diverge. */
    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
