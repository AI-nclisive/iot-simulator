package com.ainclusive.iotsim.protocolmodel;

import java.util.List;
import java.util.Objects;

/**
 * One named, typed member of a {@link NodeKind#DATA_TYPE} node's structure (IS-183), e.g. the
 * "x" member of a "Vector3D" struct.
 *
 * <p>Exactly one of {@code dataType} (a primitive) or {@code dataTypeNodeId} (the {@code nodeId}
 * of another {@link NodeKind#DATA_TYPE} node or a preserved OPC UA DataType NodeId) must be set.
 * Structured declarations may nest to any finite depth; {@link SchemaNodeValidator} rejects only
 * cycles, because a cycle has no materializable value shape.
 */
public record DataTypeMember(
        String name,
        DataType dataType,
        String dataTypeNodeId,
        ValueRank valueRank,
        List<Integer> arrayDimensions,
        boolean optional) {
    public DataTypeMember {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("member name must not be blank");
        }
        if ((dataType == null) == (dataTypeNodeId == null)) {
            throw new IllegalArgumentException(
                    "member '" + name + "' requires exactly one of dataType or dataTypeNodeId");
        }
        valueRank = valueRank == null ? ValueRank.SCALAR : valueRank;
        arrayDimensions = arrayDimensions == null ? List.of() : List.copyOf(arrayDimensions);
        if (valueRank == ValueRank.SCALAR && !arrayDimensions.isEmpty()) {
            throw new IllegalArgumentException("arrayDimensions require ARRAY member valueRank");
        }
        if (arrayDimensions.stream().anyMatch(dimension -> dimension < 0)) {
            throw new IllegalArgumentException("member arrayDimensions must be non-negative");
        }
    }

    /** Compatibility constructor for scalar, required fields authored before IS-194. */
    public DataTypeMember(String name, DataType dataType, String dataTypeNodeId) {
        this(name, dataType, dataTypeNodeId, ValueRank.SCALAR, List.of(), false);
    }
}
