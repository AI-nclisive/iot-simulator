package com.ainclusive.iotsim.api.schema;

import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.DataTypeMember;
import com.ainclusive.iotsim.protocolmodel.ReferenceType;
import com.ainclusive.iotsim.protocolmodel.SchemaReference;
import java.util.List;

/** Shared mapping for the identical schema-node reference/member fields exposed by all schema endpoints. */
public final class SchemaReferenceMapper {
    private SchemaReferenceMapper() {}

    public static List<SchemaReference> toModel(List<ReferenceDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream().map(d -> new SchemaReference(d.targetNodeId(),
                ReferenceType.valueOf(d.type()), d.forward())).toList();
    }

    /** IS-183: maps a DATA_TYPE node's member DTOs, parsing the primitive {@code dataType} if set. */
    public static List<DataTypeMember> toMembers(List<MemberDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream().map(d -> new DataTypeMember(d.name(),
                d.dataType() == null ? null : DataType.valueOf(d.dataType()), d.dataTypeNodeId())).toList();
    }
}
