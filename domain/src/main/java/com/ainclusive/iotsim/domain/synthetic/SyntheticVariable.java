package com.ainclusive.iotsim.domain.synthetic;

import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.DeterminismContext;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Binds a {@link SyntheticPattern} to one schema variable node: which node, of what
 * {@link DataType}, driven by which pattern, sampled every {@code updateRateMs}
 * (per {@code backend-specs/06_ARTIFACT_FORMATS.md} "Synthetic generation model").
 *
 * <p>{@code BYTES} and the identifier/structural types
 * ({@code STATUS_CODE}, {@code QUALIFIED_NAME}, {@code NODE_ID},
 * {@code EXPANDED_NODE_ID}, {@code XML_ELEMENT}) have no natural DYNAMIC
 * generated-signal semantics — a real device wouldn't vary a NodeId reference
 * or a GUID over time either, they're structural OPC UA plumbing, not measured
 * signals (IS-168). A dynamic pattern (ramp/sine/square/random/enum-cycle/
 * step-sequence) is therefore still rejected for them, but a {@link
 * SyntheticPattern.Constant} (fixed value) is allowed, since that's exactly
 * what these fields already are on a real device. {@code DATETIME} and {@code GUID}
 * are exceptions: a clock and per-sample correlation ID naturally vary, so they
 * support their dedicated random patterns.
 *
 * @param nodeId       target variable node id
 * @param dataType     the node's neutral type; generated values are coerced to it
 * @param pattern      the signal shape
 * @param updateRateMs sample interval in milliseconds (&gt; 0)
 */
public record SyntheticVariable(String nodeId, DataType dataType, SyntheticPattern pattern, long updateRateMs) {

    private static final Set<DataType> CONSTANT_ONLY = EnumSet.of(
            DataType.BYTES, DataType.STATUS_CODE,
            DataType.QUALIFIED_NAME, DataType.NODE_ID, DataType.EXPANDED_NODE_ID, DataType.XML_ELEMENT);

    public SyntheticVariable {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(dataType, "dataType");
        Objects.requireNonNull(pattern, "pattern");
        if (updateRateMs <= 0) {
            throw new IllegalArgumentException("updateRateMs must be > 0: " + updateRateMs);
        }
        if (CONSTANT_ONLY.contains(dataType) && !(pattern instanceof SyntheticPattern.Constant)) {
            throw new IllegalArgumentException(
                    "synthetic generation only supports a constant value for " + dataType + " (v1)");
        }
        if (dataType == DataType.DATETIME
                && !(pattern instanceof SyntheticPattern.Constant || pattern instanceof SyntheticPattern.RandomUniform)) {
            throw new IllegalArgumentException("synthetic generation only supports constant or random values for DATETIME");
        }
        if (dataType == DataType.GUID
                && !(pattern instanceof SyntheticPattern.Constant || pattern instanceof SyntheticPattern.RandomUuid)) {
            throw new IllegalArgumentException("synthetic generation only supports constant or random UUID values for GUID");
        }
    }

    /** Creates a run-scoped generator that emits this variable's series under {@code context}. */
    public SyntheticGenerator generator(DeterminismContext context) {
        return new SyntheticGenerator(this, context);
    }
}
