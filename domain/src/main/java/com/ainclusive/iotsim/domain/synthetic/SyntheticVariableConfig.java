package com.ainclusive.iotsim.domain.synthetic;

import com.ainclusive.iotsim.protocolmodel.DataType;

/**
 * One variable's synthetic binding inside a {@link SyntheticConfig}: the target
 * node, its neutral type, the pattern to generate, and the sample interval.
 * (backend-specs/06 "Synthetic generation model".)
 */
public record SyntheticVariableConfig(String nodeId, DataType dataType, PatternSpec pattern, long updateRateMs) {}
