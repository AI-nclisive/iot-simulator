package com.ainclusive.iotsim.protocolmodel;

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
public record DataTypeMember(String name, DataType dataType, String dataTypeNodeId) {
    public DataTypeMember {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("member name must not be blank");
        }
        if ((dataType == null) == (dataTypeNodeId == null)) {
            throw new IllegalArgumentException(
                    "member '" + name + "' requires exactly one of dataType or dataTypeNodeId");
        }
    }
}
