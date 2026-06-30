package com.ainclusive.iotsim.domain.synthetic;

import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.StepSequence.Step;
import java.time.Duration;
import java.util.List;

/** Maps the serialized {@link SyntheticConfig} to runnable domain {@link SyntheticVariable}s. */
public final class SyntheticConfigMapper {

    private SyntheticConfigMapper() {}

    public static List<SyntheticVariable> toVariables(SyntheticConfig config) {
        return config.variables().stream()
                .map(v -> new SyntheticVariable(v.nodeId(), v.dataType(), toPattern(v.pattern()), v.updateRateMs()))
                .toList();
    }

    public static SyntheticPattern toPattern(PatternSpec spec) {
        if (spec == null || spec.type() == null) {
            throw new IllegalArgumentException("pattern type is required");
        }
        return switch (spec.type()) {
            case "CONSTANT" -> new SyntheticPattern.Constant(req(spec.value(), "value"));
            case "RAMP" -> new SyntheticPattern.Ramp(req(spec.min(), "min"), req(spec.max(), "max"), period(spec));
            case "SINE" -> new SyntheticPattern.Sine(req(spec.min(), "min"), req(spec.max(), "max"), period(spec));
            case "SQUARE" -> new SyntheticPattern.Square(req(spec.min(), "min"), req(spec.max(), "max"), period(spec));
            case "RANDOM_UNIFORM" ->
                    new SyntheticPattern.RandomUniform(req(spec.min(), "min"), req(spec.max(), "max"));
            case "RANDOM_WALK" -> new SyntheticPattern.RandomWalk(
                    req(spec.min(), "min"), req(spec.max(), "max"), req(spec.volatility(), "volatility"));
            case "ENUM_CYCLE" -> new SyntheticPattern.EnumCycle(reqList(spec.values(), "values"));
            case "STEP_SEQUENCE" -> new SyntheticPattern.StepSequence(steps(spec));
            default -> throw new IllegalArgumentException("unknown pattern type: " + spec.type());
        };
    }

    private static List<Step> steps(PatternSpec spec) {
        return reqList(spec.steps(), "steps").stream()
                .map(s -> new Step(s.value(), Duration.ofMillis(s.holdMs())))
                .toList();
    }

    private static Duration period(PatternSpec spec) {
        return Duration.ofMillis(req(spec.periodMs(), "periodMs"));
    }

    private static double req(Double v, String name) {
        if (v == null) {
            throw new IllegalArgumentException(name + " is required for this pattern");
        }
        return v;
    }

    private static long req(Long v, String name) {
        if (v == null) {
            throw new IllegalArgumentException(name + " is required for this pattern");
        }
        return v;
    }

    private static <T> List<T> reqList(List<T> v, String name) {
        if (v == null || v.isEmpty()) {
            throw new IllegalArgumentException(name + " is required and must be non-empty for this pattern");
        }
        return v;
    }
}
