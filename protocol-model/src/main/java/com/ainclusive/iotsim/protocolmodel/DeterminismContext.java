package com.ainclusive.iotsim.protocolmodel;

import java.util.Objects;

/**
 * The run-scoped determinism pair materialized from {@link DeterministicSettings}:
 * the advanceable {@link MutableClock} a run steps as time progresses, and the
 * {@link SeededRng} it draws per-node values from.
 *
 * <p>One context belongs to one run. A generator or scenario engine advances
 * {@link #clock()} and pulls named streams from {@link #rng()}; together they make
 * the run reproducible (see {@code backend-specs/01_PROTOCOL_NEUTRAL_MODEL.md} §4).
 */
public record DeterminismContext(MutableClock clock, SeededRng rng) {

    public DeterminismContext {
        Objects.requireNonNull(clock, "clock");
        Objects.requireNonNull(rng, "rng");
    }
}
