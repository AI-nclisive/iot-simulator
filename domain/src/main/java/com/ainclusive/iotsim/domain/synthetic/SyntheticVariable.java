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
 * <p>{@code BYTES}, {@code DATETIME}, and the identifier/structural types
 * ({@code GUID}, {@code STATUS_CODE}, {@code QUALIFIED_NAME}, {@code NODE_ID},
 * {@code EXPANDED_NODE_ID}, {@code XML_ELEMENT}) have no natural generated-signal
 * semantics and are out of scope for v1, so they are rejected here.
 *
 * @param nodeId       target variable node id
 * @param dataType     the node's neutral type; generated values are coerced to it
 * @param pattern      the signal shape
 * @param updateRateMs sample interval in milliseconds (&gt; 0)
 */
public record SyntheticVariable(String nodeId, DataType dataType, SyntheticPattern pattern, long updateRateMs) {

    private static final Set<DataType> UNSUPPORTED = EnumSet.of(
            DataType.BYTES, DataType.DATETIME, DataType.GUID, DataType.STATUS_CODE,
            DataType.QUALIFIED_NAME, DataType.NODE_ID, DataType.EXPANDED_NODE_ID, DataType.XML_ELEMENT);

    public SyntheticVariable {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(dataType, "dataType");
        Objects.requireNonNull(pattern, "pattern");
        if (updateRateMs <= 0) {
            throw new IllegalArgumentException("updateRateMs must be > 0: " + updateRateMs);
        }
        if (UNSUPPORTED.contains(dataType)) {
            throw new IllegalArgumentException("synthetic generation does not support " + dataType + " in v1");
        }
    }

    /** Creates a run-scoped generator that emits this variable's series under {@code context}. */
    public SyntheticGenerator generator(DeterminismContext context) {
        return new SyntheticGenerator(this, context);
    }
}
