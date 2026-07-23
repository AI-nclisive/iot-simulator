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
 * @param dataType  required for {@link NodeKind#VARIABLE} unless {@code dataTypeNodeId} is set
 *                  instead, otherwise {@code null}
 * @param parentId  {@code null} for a root child; always {@code null} for {@link NodeKind#DATA_TYPE}
 *                  (IS-183) — a DATA_TYPE is a top-level type definition, not part of the
 *                  FOLDER/OBJECT parent-child hierarchy
 * @param typeDefinition  free-form OPC UA HasTypeDefinition target (e.g. a built-in VariableType
 *                        NodeId string parsed from NodeSet XML); orthogonal to {@code dataType}/
 *                        {@code dataTypeNodeId} and unrelated to IS-183
 * @param dataTypeNodeId  for a {@link NodeKind#VARIABLE}, the {@code nodeId} of a custom
 *                        {@link NodeKind#DATA_TYPE} node this variable's value is shaped by, used
 *                        instead of a primitive {@code dataType} (IS-183); {@code null} for other
 *                        kinds. A dedicated field rather than reusing {@code typeDefinition}: that
 *                        field already carries an unrelated, independently-optional OPC UA concept
 *                        (see above) that pre-IS-183 tests exercise alongside a primitive
 *                        {@code dataType} on the same VARIABLE.
 * @param members   ordered, named+typed members of a {@link NodeKind#DATA_TYPE} node's structure
 *                  (IS-183); empty for every other kind
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
        List<SchemaReference> references,
        String dataTypeNodeId,
        List<DataTypeMember> members) {

    public SchemaNode {
        Objects.requireNonNull(nodeId, "nodeId");
        Objects.requireNonNull(path, "path");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(kind, "kind");
        arrayDimensions = arrayDimensions == null ? List.of() : List.copyOf(arrayDimensions);
        references = references == null ? List.of() : List.copyOf(references);
        members = members == null ? List.of() : List.copyOf(members);
        if (kind == NodeKind.VARIABLE) {
            if ((dataType == null) == (dataTypeNodeId == null)) {
                throw new IllegalArgumentException(
                        "a VARIABLE node requires exactly one of dataType or dataTypeNodeId");
            }
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
        if (kind != NodeKind.VARIABLE && dataTypeNodeId != null) {
            throw new IllegalArgumentException(kind + " nodes cannot have a dataTypeNodeId");
        }
        if (kind == NodeKind.DATA_TYPE) {
            if (parentId != null) {
                throw new IllegalArgumentException("DATA_TYPE nodes must be top-level (parentId must be null)");
            }
            if (members.isEmpty()) {
                throw new IllegalArgumentException("DATA_TYPE node '" + nodeId + "' requires at least one member");
            }
            if (dataType != null) {
                throw new IllegalArgumentException("DATA_TYPE nodes cannot have a dataType field");
            }
        } else if (!members.isEmpty()) {
            throw new IllegalArgumentException(kind + " nodes cannot have members");
        }
    }

    /** Backward-compatible constructor for OPC-UA address-space nodes authored before IS-183. */
    public SchemaNode(String nodeId, String parentId, String path, String name, NodeKind kind,
            DataType dataType, ValueRank valueRank, Access access, String unit, String description,
            List<Integer> arrayDimensions, String typeDefinition, List<SchemaReference> references) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description,
                arrayDimensions, typeDefinition, references, null, List.of());
    }

    /** Backward-compatible constructor for folders and scalar/array variables authored before IS-176. */
    public SchemaNode(String nodeId, String parentId, String path, String name, NodeKind kind,
            DataType dataType, ValueRank valueRank, Access access, String unit, String description) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description,
                List.of(), null, List.of(), null, List.of());
    }
}
