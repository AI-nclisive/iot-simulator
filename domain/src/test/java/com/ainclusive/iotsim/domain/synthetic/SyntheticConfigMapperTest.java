package com.ainclusive.iotsim.domain.synthetic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.protocolmodel.DataType;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SyntheticConfigMapperTest {

    private static PatternSpec spec(String type, Double value, Double min, Double max,
            Long periodMs, Double volatility, List<Object> values, List<PatternSpec.StepSpec> steps) {
        return new PatternSpec(type, value, min, max, periodMs, volatility, values, steps);
    }

    @Test
    void mapsConstant() {
        var p = SyntheticConfigMapper.toPattern(spec("CONSTANT", 5.0, null, null, null, null, null, null));
        assertThat(p).isEqualTo(new SyntheticPattern.Constant(5.0));
    }

    @Test
    void mapsStringConstant() {
        // IS-168: a CONSTANT for a structural/identifier type carries stringValue,
        // not the numeric value field.
        var stringSpec = new PatternSpec("CONSTANT", null, null, null, null, null, null, null,
                "ns=2;s=Foo", null);
        var p = SyntheticConfigMapper.toPattern(stringSpec);
        assertThat(p).isEqualTo(new SyntheticPattern.Constant("ns=2;s=Foo"));
    }

    @Test
    void mapsBytesConstantFromBase64() {
        var bytesSpec = new PatternSpec("CONSTANT", null, null, null, null, null, null, null,
                null, java.util.Base64.getEncoder().encodeToString(new byte[] {1, 2, 3}));
        var p = SyntheticConfigMapper.toPattern(bytesSpec);
        assertThat((byte[]) ((SyntheticPattern.Constant) p).value()).containsExactly(1, 2, 3);
    }

    @Test
    void mapsIsoDateTimeConstantToEpochMillis() {
        var p = SyntheticConfigMapper.toPattern(new PatternSpec("CONSTANT", null, null, null, null,
                null, null, null, null, null, "2026-07-22T08:06:13.217Z"));
        assertThat(p).isEqualTo(new SyntheticPattern.Constant(
                Instant.parse("2026-07-22T08:06:13.217Z").toEpochMilli()));
    }

    @Test
    void rejectsInvalidIsoDateTimeConstant() {
        assertThatThrownBy(() -> SyntheticConfigMapper.toPattern(new PatternSpec("CONSTANT", null,
                null, null, null, null, null, null, null, null, "22/07/2026")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ISO-8601");
    }

    @Test
    void mapsRandomChoiceList() {
        var p = SyntheticConfigMapper.toPattern(spec("RANDOM_CHOICE", null, null, null, null, null,
                List.of("Idle", "Running", "Alarm"), null));
        assertThat(p).isEqualTo(new SyntheticPattern.RandomChoice(List.of("Idle", "Running", "Alarm")));
    }

    @Test
    void constantWithNoValueShapeRejected() {
        var emptySpec = new PatternSpec("CONSTANT", null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> SyntheticConfigMapper.toPattern(emptySpec))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("value");
    }

    @Test
    void mapsSineWithPeriodMillis() {
        var p = SyntheticConfigMapper.toPattern(spec("SINE", null, 0.0, 10.0, 2000L, null, null, null));
        assertThat(p).isEqualTo(new SyntheticPattern.Sine(0.0, 10.0, Duration.ofMillis(2000)));
    }

    @Test
    void mapsStepSequenceWithHoldMillis() {
        var p = SyntheticConfigMapper.toPattern(spec("STEP_SEQUENCE", null, null, null, null, null, null,
                List.of(new PatternSpec.StepSpec("a", 1000L), new PatternSpec.StepSpec("b", 2000L))));
        assertThat(p).isEqualTo(new SyntheticPattern.StepSequence(List.of(
                new SyntheticPattern.StepSequence.Step("a", Duration.ofMillis(1000)),
                new SyntheticPattern.StepSequence.Step("b", Duration.ofMillis(2000)))));
    }

    @Test
    void unknownTypeRejected() {
        assertThatThrownBy(() -> SyntheticConfigMapper.toPattern(
                spec("WOBBLE", null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("WOBBLE");
    }

    @Test
    void missingRequiredParamRejected() {
        assertThatThrownBy(() -> SyntheticConfigMapper.toPattern(
                spec("RAMP", null, 0.0, 10.0, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodMs");
    }

    @Test
    void toVariablesMapsEachVariable() {
        var config = new SyntheticConfig(7L, List.of(
                new SyntheticVariableConfig("n1", DataType.FLOAT64,
                        spec("CONSTANT", 1.0, null, null, null, null, null, null), 500)));
        List<SyntheticVariable> vars = SyntheticConfigMapper.toVariables(config);
        assertThat(vars).containsExactly(
                new SyntheticVariable("n1", DataType.FLOAT64, new SyntheticPattern.Constant(1.0), 500));
    }

    @Test
    void acceptsStringConstantAndListPatterns() {
        var constant = new SyntheticVariableConfig("state", DataType.STRING,
                new PatternSpec("CONSTANT", null, null, null, null, null, null, null,
                        "Running", null), 500);
        var random = new SyntheticVariableConfig("state2", DataType.LOCALIZED_TEXT,
                spec("RANDOM_CHOICE", null, null, null, null, null, List.of("Idle", "Alarm"), null), 500);

        assertThat(SyntheticConfigMapper.toVariables(new SyntheticConfig(7L, List.of(constant, random))))
                .extracting(SyntheticVariable::pattern)
                .containsExactly(new SyntheticPattern.Constant("Running"),
                        new SyntheticPattern.RandomChoice(List.of("Idle", "Alarm")));
    }

    @Test
    void rejectsNumericStringConstantAndNonStringListMembers() {
        var numericConstant = new SyntheticVariableConfig("state", DataType.STRING,
                spec("CONSTANT", 1.0, null, null, null, null, null, null), 500);
        var mixedList = new SyntheticVariableConfig("state2", DataType.LOCALIZED_TEXT,
                spec("ENUM_CYCLE", null, null, null, null, null, List.of("Idle", 1.0), null), 500);

        assertThatThrownBy(() -> SyntheticConfigMapper.toVariables(new SyntheticConfig(7L, List.of(numericConstant))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("stringValue");
        assertThatThrownBy(() -> SyntheticConfigMapper.toVariables(new SyntheticConfig(7L, List.of(mixedList))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must all be strings");
    }

    @Test
    void rejectsNumericSignalShapesForText() {
        var variable = new SyntheticVariableConfig("state", DataType.STRING,
                spec("RANDOM_UNIFORM", null, 0.0, 1.0, null, null, null, null), 500);

        assertThatThrownBy(() -> SyntheticConfigMapper.toVariables(new SyntheticConfig(7L, List.of(variable))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("only supports");
    }

    @Test
    void rejectsFractionalIntegerPatternParameters() {
        var variable = new SyntheticVariableConfig("counter", DataType.INT32,
                spec("RANDOM_UNIFORM", null, 0.5, 10.0, null, null, null, null), 500);

        assertThatThrownBy(() -> SyntheticConfigMapper.toVariables(new SyntheticConfig(7L, List.of(variable))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("whole number");
    }

    @Test
    void requiresIsoShapeForDateTimeVariable() {
        var variable = new SyntheticVariableConfig("timestamp", DataType.DATETIME,
                spec("CONSTANT", 1.0, null, null, null, null, null, null), 500);

        assertThatThrownBy(() -> SyntheticConfigMapper.toVariables(new SyntheticConfig(7L, List.of(variable))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dateTimeValue");
    }

    @Test
    void emptyVariablesRejected() {
        assertThatThrownBy(() -> new SyntheticConfig(1L, List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one variable");
    }

    @Test
    void duplicateNodeIdRejected() {
        var v = new SyntheticVariableConfig("dup", DataType.INT32,
                spec("CONSTANT", 1.0, null, null, null, null, null, null), 100);
        assertThatThrownBy(() -> new SyntheticConfig(1L, List.of(v, v)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dup");
    }

    @Test
    void jsonRoundTripPreservesConfig() {
        ObjectMapper json = new ObjectMapper();
        var config = new SyntheticConfig(42L, List.of(
                new SyntheticVariableConfig("temp", DataType.FLOAT64,
                        spec("SINE", null, 0.0, 100.0, 1000L, null, null, null), 250)));
        String text = json.writeValueAsString(config);
        SyntheticConfig back = json.readValue(text, SyntheticConfig.class);
        assertThat(back).isEqualTo(config);
    }
}
