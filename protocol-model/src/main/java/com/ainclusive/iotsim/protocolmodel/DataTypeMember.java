package com.ainclusive.iotsim.protocolmodel;

import java.util.Objects;

/**
 * One named, typed member of a {@link NodeKind#DATA_TYPE} node's structure (IS-183), e.g. the
 * "x" member of a "Vector3D" struct.
 *
 * <p>Exactly one of {@code dataType} (a primitive) or {@code dataTypeNodeId} (the {@code nodeId}
 * of another {@link NodeKind#DATA_TYPE} node) must be set — v1 keeps custom types to one level of
 * struct-of-primitives, so a member that nests another custom type may not itself nest a further
 * custom type; see {@link SchemaNodeValidator}.
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
