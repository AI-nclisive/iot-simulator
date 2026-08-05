package com.ainclusive.iotsim.platform.scan;

import com.ainclusive.iotsim.protocolmodel.DataTypeEnumValue;
import com.ainclusive.iotsim.protocolmodel.DataTypeMember;
import com.ainclusive.iotsim.protocolmodel.NativeTypeKind;
import java.util.List;

/**
 * One node discovered by a scan, in protocol-neutral terms. Distinct from
 * {@code protocol-model.SchemaNode}: a discovered VARIABLE may have a {@code null}
 * {@code dataType} ("unknown" — outside the neutral type set), which a SchemaNode
 * forbids. Unknown types are surfaced for the user to resolve before they become a
 * persisted schema (openspec/specs/protocol-model/spec.md §2; resolution is IS-044).
 *
 * @param kind {@code FOLDER} or {@code VARIABLE}
 * @param dataType neutral data type for a VARIABLE, or {@code null} if its declaration
 *                 is represented by {@code dataTypeNodeId}
 * @param dataTypeNodeId original native DataType NodeId for a VARIABLE whose declaration
 *                       cannot be reduced to a neutral primitive; never a guessed type
 */
public record DiscoveredNode(String nodeId, String parentId, String path, String name,
        String kind, String dataType, String valueRank, String access,
        String unit, String description, String dataTypeNodeId, List<DataTypeMember> dataTypeMembers,
        List<DataTypeEnumValue> dataTypeEnumValues, String dataTypeDefaultEncodingId,
        NativeTypeKind dataTypeKind,
        List<DiscoveredTypeDefinition> dataTypeDependencies,
        String dataTypeName) {

    public DiscoveredNode {
        dataTypeMembers = dataTypeMembers == null ? List.of() : List.copyOf(dataTypeMembers);
        dataTypeEnumValues = dataTypeEnumValues == null ? List.of() : List.copyOf(dataTypeEnumValues);
        dataTypeDependencies = dataTypeDependencies == null ? List.of() : List.copyOf(dataTypeDependencies);
    }

    /** Compatibility constructor for callers that have no native type declaration. */
    public DiscoveredNode(String nodeId, String parentId, String path, String name,
            String kind, String dataType, String valueRank, String access,
            String unit, String description) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access,
                unit, description, null, List.of(), List.of(), null, null, List.of(), null);
    }

    /** Compatibility constructor for a native DataType declaration without its definition. */
    public DiscoveredNode(String nodeId, String parentId, String path, String name,
            String kind, String dataType, String valueRank, String access,
            String unit, String description, String dataTypeNodeId) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access,
                unit, description, dataTypeNodeId, List.of(), List.of(), null, null, List.of(), null);
    }

    /** Compatibility constructor for a native structured declaration without enum literals. */
    public DiscoveredNode(String nodeId, String parentId, String path, String name,
            String kind, String dataType, String valueRank, String access,
            String unit, String description, String dataTypeNodeId, List<DataTypeMember> dataTypeMembers) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access,
                unit, description, dataTypeNodeId, dataTypeMembers, List.of(), null, null, List.of(), null);
    }

    /** Compatibility constructor for a native declaration without structure encoding metadata. */
    public DiscoveredNode(String nodeId, String parentId, String path, String name,
            String kind, String dataType, String valueRank, String access,
            String unit, String description, String dataTypeNodeId, List<DataTypeMember> dataTypeMembers,
            List<DataTypeEnumValue> dataTypeEnumValues) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description,
                dataTypeNodeId, dataTypeMembers, dataTypeEnumValues, null, null, List.of(), null);
    }

    /** Compatibility constructor for scan results without a transitive type catalog. */
    public DiscoveredNode(String nodeId, String parentId, String path, String name,
            String kind, String dataType, String valueRank, String access,
            String unit, String description, String dataTypeNodeId, List<DataTypeMember> dataTypeMembers,
            List<DataTypeEnumValue> dataTypeEnumValues, String dataTypeDefaultEncodingId) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description,
                dataTypeNodeId, dataTypeMembers, dataTypeEnumValues, dataTypeDefaultEncodingId, null, List.of(), null);
    }

    /** Compatibility constructor for scan results with dependencies but no explicit kind. */
    public DiscoveredNode(String nodeId, String parentId, String path, String name,
            String kind, String dataType, String valueRank, String access,
            String unit, String description, String dataTypeNodeId, List<DataTypeMember> dataTypeMembers,
            List<DataTypeEnumValue> dataTypeEnumValues, String dataTypeDefaultEncodingId,
            List<DiscoveredTypeDefinition> dataTypeDependencies) {
        this(nodeId, parentId, path, name, kind, dataType, valueRank, access, unit, description,
                dataTypeNodeId, dataTypeMembers, dataTypeEnumValues, dataTypeDefaultEncodingId, null,
                dataTypeDependencies, null);
    }

    /** True for a VARIABLE whose type could not be mapped to the neutral set. */
    public boolean isUnknownType() {
        return "VARIABLE".equals(kind) && (dataType == null || dataType.isBlank());
    }
}
