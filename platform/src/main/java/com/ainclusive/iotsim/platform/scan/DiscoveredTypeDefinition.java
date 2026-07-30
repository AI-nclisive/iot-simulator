package com.ainclusive.iotsim.platform.scan;

import com.ainclusive.iotsim.protocolmodel.DataTypeEnumValue;
import com.ainclusive.iotsim.protocolmodel.DataTypeMember;
import com.ainclusive.iotsim.protocolmodel.NativeTypeKind;
import java.util.List;
import java.util.Objects;

/**
 * A native OPC UA DataType declaration discovered independently of a Variable.
 *
 * <p>This keeps the transitive type catalog separate from the address-space tree: a field's
 * referenced type can be retained even when the server exposes no variable of that type.
 */
public record DiscoveredTypeDefinition(
        String nodeId,
        String name,
        List<DataTypeMember> members,
        List<DataTypeEnumValue> enumValues,
        String defaultEncodingId,
        NativeTypeKind nativeTypeKind) {

    public DiscoveredTypeDefinition {
        Objects.requireNonNull(nodeId, "nodeId");
        name = name == null || name.isBlank() ? nodeId : name;
        members = members == null ? List.of() : List.copyOf(members);
        enumValues = enumValues == null ? List.of() : List.copyOf(enumValues);
    }

    public DiscoveredTypeDefinition(String nodeId, String name, List<DataTypeMember> members,
            List<DataTypeEnumValue> enumValues, String defaultEncodingId) {
        this(nodeId, name, members, enumValues, defaultEncodingId, null);
    }
}
