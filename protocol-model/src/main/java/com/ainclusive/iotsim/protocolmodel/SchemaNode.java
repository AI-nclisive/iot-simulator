package com.ainclusive.iotsim.protocolmodel;

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
        String description) {

    public SchemaNode {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        if (kind == NodeKind.VARIABLE) {
            Objects.requireNonNull(dataType, "dataType is required for a VARIABLE node");
            Objects.requireNonNull(valueRank, "valueRank is required for a VARIABLE node");
            Objects.requireNonNull(access, "access is required for a VARIABLE node");
        }
    }
}
