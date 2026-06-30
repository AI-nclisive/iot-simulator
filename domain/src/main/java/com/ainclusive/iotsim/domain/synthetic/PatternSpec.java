package com.ainclusive.iotsim.domain.synthetic;

import java.util.List;

/**
 * Serialized form of a {@link SyntheticPattern}: a {@code type} discriminator plus
 * the union of pattern parameters (only those relevant to the type are set).
 * Durations are milliseconds. {@link SyntheticConfigMapper} maps this to the domain
 * pattern. (backend-specs/06 "Synthetic generation model".)
 */
public record PatternSpec(
        String type,
        Double value,
        Double min,
        Double max,
        Long periodMs,
        Double volatility,
        List<Object> values,
        List<StepSpec> steps) {

    /** One step of a STEP_SEQUENCE: a value held for {@code holdMs} milliseconds. */
    public record StepSpec(Object value, long holdMs) {}
}
