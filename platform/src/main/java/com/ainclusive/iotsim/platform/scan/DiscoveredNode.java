package com.ainclusive.iotsim.platform.scan;

import com.ainclusive.iotsim.protocolmodel.DataTypeMember;
import java.util.List;

/**
 * One node discovered by a scan, in protocol-neutral terms. Distinct from
 * {@code protocol-model.SchemaNode}: a discovered VARIABLE may have a {@code null}
 * {@code dataType} ("unknown" — outside the neutral type set), which a SchemaNode
 * forbids. Unknown types are surfaced for the user to resolve before they become a
 * persisted schema (backend-specs/01 §2; resolution is IS-044).
 *
 * @param kind {@code FOLDER} or {@code VARIABLE}
 * @param dataType neutral data type for a VARIABLE, or {@code null} if its declaration
 *                 is represented by {@code dataTypeNodeId}
 * @param dataTypeNodeId original native DataType NodeId for a VARIABLE whose declaration
 *                       cannot be reduced to a neutral primitive; never a guessed type
 */
public record DiscoveredNode(String nodeId, String parentId, String path, String name,
        String kind, String dataType, String valueRank, String access,
        String unit, String description, String dataTypeNodeId, List<DataTypeMember> dataTypeMembers) {

    public DiscoveredNode {
        dataTypeMembers = dataTypeMembers == null ? List.of() : List.copyOf(dataTypeMembers);
    }

    /** Compatibility constructor for callers that have no native type declaration. */
    public DiscoveredNode(String nodeId, String parentId, String path, String name,
            String kind, String dataType, String valueRank, String access,
            String unit, String description) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access,
                unit, description, null, List.of());
    }

    /** Compatibility constructor for a native DataType declaration without its definition. */
    public DiscoveredNode(String nodeId, String parentId, String path, String name,
            String kind, String dataType, String valueRank, String access,
            String unit, String description, String dataTypeNodeId) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access,
                unit, description, dataTypeNodeId, List.of());
    }

    /** True for a VARIABLE whose type could not be mapped to the neutral set. */
    public boolean isUnknownType() {
        return "VARIABLE".equals(kind) && (dataType == null || dataType.isBlank());
    }
}
