package com.ainclusive.iotsim.domain.synthetic;

import java.util.List;

/**
 * Serialized form of a {@link SyntheticPattern}: a {@code type} discriminator plus
 * the union of pattern parameters (only those relevant to the type are set).
 * Durations are milliseconds. {@link SyntheticConfigMapper} maps this to the domain
 * pattern. (backend-specs/06 "Synthetic generation model".)
 *
 * <p>A {@code CONSTANT} pattern carries its value in exactly one of three shapes
 * (IS-168): {@code value} for the common numeric case (unchanged from before),
 * {@code stringValue} for the identifier/structural string-shaped types
 * ({@code GUID}, {@code QUALIFIED_NAME}, {@code NODE_ID},
 * {@code EXPANDED_NODE_ID}, {@code XML_ELEMENT}), or {@code bytesValueBase64}
 * (standard Base64) for {@code BYTES}.
 */
public record PatternSpec(
        String type,
        Double value,
        Double min,
        Double max,
        Long periodMs,
        Double volatility,
        List<Object> values,
        List<StepSpec> steps,
        String stringValue,
        String bytesValueBase64) {

    /** Pre-IS-168 shape, for callers that never carry a non-numeric CONSTANT. */
    public PatternSpec(String type, Double value, Double min, Double max, Long periodMs,
            Double volatility, List<Object> values, List<StepSpec> steps) {
        this(type, value, min, max, periodMs, volatility, values, steps, null, null);
    }

    /** One step of a STEP_SEQUENCE: a value held for {@code holdMs} milliseconds. */
    public record StepSpec(Object value, long holdMs) {}
}
