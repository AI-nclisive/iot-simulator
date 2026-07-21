package com.ainclusive.iotsim.protocolmodel;

import java.util.List;
import java.util.Objects;

/**
 * One addressable node in a protocol-neutral schema tree.
 *
 * <p>{@code nodeId} is the only stable reference (used by recordings, samples,
 * scenarios, faults, evidence). {@code path} is unique within a schema. See
 * {@code backend-specs/01_PROTOCOL_NEUTRAL_MODEL.md} §1.
 *
 * @param dataType  required for {@link NodeKind#VARIABLE}, otherwise {@code null}
 * @param parentId  {@code null} for a root child
 */
public record SchemaNode(
        String nodeId,
        String parentId,
        String path,
        String name,
        NodeKind kind,
        DataType dataType,
        ValueRank valueRank,
        Access access,
        String unit,
        String description,
        List<Integer> arrayDimensions,
        String typeDefinition,
        List<SchemaReference> references) {

    public SchemaNode {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        arrayDimensions = arrayDimensions == null ? List.of() : List.copyOf(arrayDimensions);
        references = references == null ? List.of() : List.copyOf(references);
        if (kind == NodeKind.VARIABLE) {
            Objects.requireNonNull(dataType, "dataType is required for a VARIABLE node");
            Objects.requireNonNull(valueRank, "valueRank is required for a VARIABLE node");
            Objects.requireNonNull(access, "access is required for a VARIABLE node");
            if (valueRank == ValueRank.SCALAR && !arrayDimensions.isEmpty()) {
                throw new IllegalArgumentException("arrayDimensions require ARRAY valueRank");
            }
            if (arrayDimensions.stream().anyMatch(dimension -> dimension < 0)) {
                throw new IllegalArgumentException("arrayDimensions must be non-negative");
            }
        } else if (!arrayDimensions.isEmpty()) {
            throw new IllegalArgumentException(kind + " nodes cannot have array dimensions");
        }
    }

    /** Backward-compatible constructor for folders and scalar/array variables authored before IS-176. */
    public SchemaNode(String nodeId, String parentId, String path, String name, NodeKind kind,
            DataType dataType, ValueRank valueRank, Access access, String unit, String description) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description,
                List.of(), null, List.of());
    }
}
