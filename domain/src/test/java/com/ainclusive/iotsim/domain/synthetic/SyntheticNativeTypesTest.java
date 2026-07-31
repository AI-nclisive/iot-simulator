package com.ainclusive.iotsim.domain.synthetic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.protocolmodel.DataType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Tests for IS-200: synthetic sources from manual native OPC UA schemas.
 * Demonstrates CONSTANT patterns for STRUCTURE, UNION, ENUM, and OPTION_SET types.
 */
class SyntheticNativeTypesTest {

    @Test
    void constantStructureValueAccepted() {
        // IS-200: A CONSTANT pattern for a native structure type
        Map<String, Object> rangeValue = new LinkedHashMap<>();
        rangeValue.put("low", 10.0);
        rangeValue.put("high", 100.0);

        var spec = new PatternSpec("CONSTANT", null, null, null, null, null, null, null,
                null, null, null, null, null, rangeValue);
        var pattern = SyntheticConfigMapper.toPattern(spec);
        assertThat(pattern).isEqualTo(new SyntheticPattern.Constant(rangeValue));
    }

    @Test
    void nativeTypeVariableOnlyAcceptsConstantPattern() {
        // IS-200: native type variables reject dynamic patterns
        assertThatThrownBy(() -> {
            new SyntheticVariable(
                    "myStructVar",
                    null,  // dataType null for native types
                    new SyntheticPattern.Ramp(0.0, 10.0, java.time.Duration.ofSeconds(1)),
                    1000,
                    "myStructTypeId");  // dataTypeNodeId references a native type
        })
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CONSTANT patterns");
    }

    @Test
    void nativeTypeVariableWithConstantPatternAccepted() {
        // IS-200: native type variables accept CONSTANT patterns
        Map<String, Object> structValue = Map.of("field1", "value1");
        var variable = new SyntheticVariable(
                "myStructVar",
                null,
                new SyntheticPattern.Constant(structValue),
                1000,
                "myStructTypeId");
        assertThat(variable.dataTypeNodeId()).isEqualTo("myStructTypeId");
        assertThat(variable.dataType()).isNull();
    }

    @Test
    void syntheticConfigValidatesNativeTypeOnlyAcceptsConstant() {
        // IS-200: mapper validates that native types only use CONSTANT patterns
        var spec = new PatternSpec("RAMP", 0.0, 0.0, 10.0, 1000L, null, null, null);
        assertThatThrownBy(() ->
                SyntheticConfigMapper.toVariables(new SyntheticConfig(1L, List.of(
                        new SyntheticVariableConfig("node1", null, spec, 1000, "structTypeId")))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CONSTANT patterns");
    }

    @Test
    void syntheticConfigMapperHandlesNativeTypeVariables() {
        // IS-200: mapper correctly creates SyntheticVariable with dataTypeNodeId
        Map<String, Object> enumValue = Map.of("value", 1L, "name", "Running");
        var spec = new PatternSpec("CONSTANT", null, null, null, null, null, null, null,
                null, null, null, null, null, enumValue);
        var config = new SyntheticConfig(1L, List.of(
                new SyntheticVariableConfig("enumNode", null, spec, 1000, "enumTypeId")));

        var variables = SyntheticConfigMapper.toVariables(config);
        assertThat(variables).hasSize(1);
        var variable = variables.get(0);
        assertThat(variable.nodeId()).isEqualTo("enumNode");
        assertThat(variable.dataType()).isNull();
        assertThat(variable.dataTypeNodeId()).isEqualTo("enumTypeId");
        assertThat(variable.pattern()).isInstanceOf(SyntheticPattern.Constant.class);
    }

    @Test
    void backwardCompatibilityNativeTypeConfigWithoutDataTypeNodeId() {
        // Backward compatibility: SyntheticVariableConfig can be created without dataTypeNodeId
        var spec = new PatternSpec("CONSTANT", 5.0, null, null, null, null, null, null);
        var config = new SyntheticVariableConfig("node1", DataType.FLOAT64, spec, 1000);
        assertThat(config.dataTypeNodeId()).isNull();
        assertThat(config.dataType()).isEqualTo(DataType.FLOAT64);
    }

    @Test
    void syntheticVariableBackwardCompatibilityConstructor() {
        // Backward compatibility: SyntheticVariable can be created without dataTypeNodeId
        var variable = new SyntheticVariable(
                "node1",
                DataType.FLOAT64,
                new SyntheticPattern.Constant(5.0),
                1000);
        assertThat(variable.dataTypeNodeId()).isNull();
        assertThat(variable.dataType()).isEqualTo(DataType.FLOAT64);
    }
}
