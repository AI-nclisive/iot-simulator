package com.ainclusive.iotsim.domain.synthetic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.protocolmodel.DataType;
import java.time.Duration;
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
