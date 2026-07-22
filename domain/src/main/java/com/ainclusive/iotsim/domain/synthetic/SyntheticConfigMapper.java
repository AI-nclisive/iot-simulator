package com.ainclusive.iotsim.domain.synthetic;

import com.ainclusive.iotsim.domain.synthetic.SyntheticPattern.StepSequence.Step;
import com.ainclusive.iotsim.protocolmodel.DataType;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

/** Maps the serialized {@link SyntheticConfig} to runnable domain {@link SyntheticVariable}s. */
public final class SyntheticConfigMapper {

    private SyntheticConfigMapper() {}

    public static List<SyntheticVariable> toVariables(SyntheticConfig config) {
        return config.variables().stream()
                .map(v -> {
                    validateForType(v.dataType(), v.pattern());
                    return new SyntheticVariable(v.nodeId(), v.dataType(), toPattern(v.pattern()), v.updateRateMs());
                })
                .toList();
    }

    public static SyntheticPattern toPattern(PatternSpec spec) {
        if (spec == null || spec.type() == null) {
            throw new IllegalArgumentException("pattern type is required");
        }
        return switch (spec.type()) {
            case "CONSTANT" -> new SyntheticPattern.Constant(constantValue(spec));
            case "RAMP" -> new SyntheticPattern.Ramp(req(spec.min(), "min"), req(spec.max(), "max"), period(spec));
            case "SINE" -> new SyntheticPattern.Sine(req(spec.min(), "min"), req(spec.max(), "max"), period(spec));
            case "SQUARE" -> new SyntheticPattern.Square(req(spec.min(), "min"), req(spec.max(), "max"), period(spec));
            case "RANDOM_UNIFORM" -> new SyntheticPattern.RandomUniform(
                    spec.dateTimeMin() != null || spec.dateTimeMax() != null
                            ? isoEpoch(spec.dateTimeMin(), "dateTimeMin")
                            : req(spec.min(), "min"),
                    spec.dateTimeMin() != null || spec.dateTimeMax() != null
                            ? isoEpoch(spec.dateTimeMax(), "dateTimeMax")
                            : req(spec.max(), "max"));
            case "RANDOM_WALK" -> new SyntheticPattern.RandomWalk(
                    req(spec.min(), "min"), req(spec.max(), "max"), req(spec.volatility(), "volatility"));
            case "ENUM_CYCLE" -> new SyntheticPattern.EnumCycle(reqList(spec.values(), "values"));
            case "RANDOM_CHOICE" -> new SyntheticPattern.RandomChoice(reqList(spec.values(), "values"));
            case "RANDOM_UUID" -> new SyntheticPattern.RandomUuid();
            case "STEP_SEQUENCE" -> new SyntheticPattern.StepSequence(steps(spec));
            default -> throw new IllegalArgumentException("unknown pattern type: " + spec.type());
        };
    }

    /** A CONSTANT's value arrives in one type-appropriate serialized shape. */
    private static Object constantValue(PatternSpec spec) {
        if (spec.value() != null) {
            return spec.value();
        }
        if (spec.stringValue() != null) {
            return spec.stringValue();
        }
        if (spec.bytesValueBase64() != null) {
            return Base64.getDecoder().decode(spec.bytesValueBase64());
        }
        if (spec.dateTimeValue() != null) {
            try {
                return Instant.parse(spec.dateTimeValue()).toEpochMilli();
            } catch (RuntimeException e) {
                throw new IllegalArgumentException("dateTimeValue must be an ISO-8601 instant", e);
            }
        }
        throw new IllegalArgumentException(
                "one of value, stringValue, bytesValueBase64, or dateTimeValue is required for the CONSTANT pattern");
    }

    private static void validateForType(DataType dataType, PatternSpec spec) {
        if (spec == null || spec.type() == null) {
            return; // toPattern supplies the standard required-field error
        }
        if (dataType == DataType.DATETIME) {
            requireOneOf(spec.type(), dataType, "CONSTANT", "RANDOM_UNIFORM");
            if ("CONSTANT".equals(spec.type())) {
                if (spec.dateTimeValue() == null) {
                    throw new IllegalArgumentException("DATETIME CONSTANT requires dateTimeValue as an ISO-8601 instant");
                }
                if (spec.value() != null || spec.stringValue() != null || spec.bytesValueBase64() != null) {
                    throw new IllegalArgumentException("DATETIME CONSTANT must use only dateTimeValue");
                }
            }
            if ("RANDOM_UNIFORM".equals(spec.type())) {
                if (spec.dateTimeMin() == null || spec.dateTimeMax() == null) {
                    throw new IllegalArgumentException("DATETIME RANDOM_UNIFORM requires dateTimeMin and dateTimeMax as ISO-8601 instants");
                }
                long min = isoEpoch(spec.dateTimeMin(), "dateTimeMin");
                long max = isoEpoch(spec.dateTimeMax(), "dateTimeMax");
                if (min > max) {
                    throw new IllegalArgumentException("dateTimeMin must be before or equal to dateTimeMax");
                }
            }
        }
        if (dataType != DataType.DATETIME
                && (spec.dateTimeValue() != null || spec.dateTimeMin() != null || spec.dateTimeMax() != null)) {
            throw new IllegalArgumentException("ISO-8601 DATETIME fields are only valid for DATETIME variables");
        }
        if (dataType == DataType.STRING || dataType == DataType.LOCALIZED_TEXT) {
            requireOneOf(spec.type(), dataType, "CONSTANT", "ENUM_CYCLE", "RANDOM_CHOICE");
            if ("CONSTANT".equals(spec.type()) && spec.stringValue() == null) {
                throw new IllegalArgumentException(dataType + " CONSTANT requires stringValue");
            }
            if (("ENUM_CYCLE".equals(spec.type()) || "RANDOM_CHOICE".equals(spec.type()))
                    && reqList(spec.values(), "values").stream().anyMatch(v -> !(v instanceof String))) {
                throw new IllegalArgumentException(dataType + " list values must all be strings");
            }
        }
        if (dataType == DataType.BOOL) {
            requireOneOf(spec.type(), dataType, "CONSTANT", "ENUM_CYCLE", "RANDOM_CHOICE");
            if (("ENUM_CYCLE".equals(spec.type()) || "RANDOM_CHOICE".equals(spec.type()))
                    && reqList(spec.values(), "values").stream().anyMatch(v -> !(v instanceof Boolean))) {
                throw new IllegalArgumentException("BOOL list values must all be booleans");
            }
        }
        if (dataType == DataType.GUID) {
            requireOneOf(spec.type(), dataType, "CONSTANT", "RANDOM_UUID");
        }
        if (isInteger(dataType)) {
            requireIntegerFields(spec);
        }
    }

    private static boolean isInteger(DataType type) {
        return switch (type) {
            case INT8, UINT8, INT16, UINT16, INT32, UINT32, INT64, UINT64 -> true;
            default -> false;
        };
    }

    private static void requireOneOf(String patternType, DataType dataType, String... allowed) {
        for (String candidate : allowed) {
            if (candidate.equals(patternType)) {
                return;
            }
        }
        throw new IllegalArgumentException(dataType + " only supports " + String.join(", ", allowed));
    }

    private static void requireIntegerFields(PatternSpec spec) {
        requireWhole(spec.value(), "value");
        requireWhole(spec.min(), "min");
        requireWhole(spec.max(), "max");
        requireWhole(spec.volatility(), "volatility");
        if (spec.values() != null && spec.values().stream().anyMatch(v -> !(v instanceof Number n)
                || !isWhole(n.doubleValue()))) {
            throw new IllegalArgumentException("integer list values must be whole numbers");
        }
    }

    private static void requireWhole(Double value, String name) {
        if (value != null && !isWhole(value)) {
            throw new IllegalArgumentException(name + " must be a whole number for an integer data type");
        }
    }

    private static boolean isWhole(double value) {
        return Double.isFinite(value) && value == Math.rint(value);
    }

    private static long isoEpoch(String value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required for this pattern");
        }
        try {
            return Instant.parse(value).toEpochMilli();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException(name + " must be an ISO-8601 instant", e);
        }
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
