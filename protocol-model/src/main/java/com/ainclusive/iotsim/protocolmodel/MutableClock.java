package com.ainclusive.iotsim.protocolmodel;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * An advanceable {@link Clock} — the time half of the determinism foundation in
 * {@code backend-specs/01_PROTOCOL_NEUTRAL_MODEL.md} §4.
 *
 * <p>Production code depends on the standard injectable {@link Clock}; a
 * deterministic run injects this implementation so simulated time steps forward
 * under explicit control ({@link #advance}, {@link #setInstant}) instead of reading
 * wall-clock time. Time only moves forward via {@link #advance}; {@link #setInstant}
 * resets it (e.g. at run start).
 *
 * <p>Reads are safe to observe across threads; compound advance-and-read sequences
 * are not atomic, which matches the single-stepping model of a deterministic run.
 */
public final class MutableClock extends Clock {

    private final ZoneId zone;
    private volatile Instant instant;

    private MutableClock(Instant instant, ZoneId zone) {
        this.instant = instant;
        this.zone = zone;
    }

    /** Creates a clock fixed at {@code instant}, reporting times in {@code zone}. */
    public static MutableClock at(Instant instant, ZoneId zone) {
        Objects.requireNonNull(instant, "instant");
        Objects.requireNonNull(zone, "zone");
        return new MutableClock(instant, zone);
    }

    /** Moves the clock forward by {@code amount} (must be non-negative). */
    public void advance(Duration amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.isNegative()) {
            throw new IllegalArgumentException("clock cannot advance by a negative duration: " + amount);
        }
        this.instant = this.instant.plus(amount);
    }

    /** Resets the clock to {@code newInstant}. */
    public void setInstant(Instant newInstant) {
        this.instant = Objects.requireNonNull(newInstant, "newInstant");
    }

    @Override
    public Instant instant() {
        return instant;
    }

    @Override
    public ZoneId getZone() {
        return zone;
    }

    @Override
    public Clock withZone(ZoneId newZone) {
        Objects.requireNonNull(newZone, "newZone");
        return new Clock() {
            @Override
            public Instant instant() {
                return MutableClock.this.instant();
            }

            @Override
            public ZoneId getZone() {
                return newZone;
            }

            @Override
            public Clock withZone(ZoneId z) {
                return MutableClock.this.withZone(z);
            }
        };
    }
}
