package com.ainclusive.iotsim.domain.synthetic;

import com.ainclusive.iotsim.protocolmodel.DataType;

/**
 * One variable's synthetic binding inside a {@link SyntheticConfig}: the target
 * node, its neutral type, the pattern to generate, and the sample interval.
 * (backend-specs/06 "Synthetic generation model".)
 *
 * <p>IS-200: For native type variables, {@code dataType} is null and {@code dataTypeNodeId}
 * references a native type declaration (STRUCTURE, ENUM, UNION, or OPTION_SET) in the schema.
 */
public record SyntheticVariableConfig(
        String nodeId, DataType dataType, PatternSpec pattern, long updateRateMs, String dataTypeNodeId) {

    public SyntheticVariableConfig(String nodeId, DataType dataType, PatternSpec pattern, long updateRateMs) {
        this(nodeId, dataType, pattern, updateRateMs, null);
    }
}
