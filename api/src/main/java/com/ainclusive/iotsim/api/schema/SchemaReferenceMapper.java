package com.ainclusive.iotsim.api.schema;

import com.ainclusive.iotsim.protocolmodel.ReferenceType;
import com.ainclusive.iotsim.protocolmodel.SchemaReference;
import java.util.List;

/** Shared mapping for the identical schema-node reference fields exposed by all schema endpoints. */
public final class SchemaReferenceMapper {
    private SchemaReferenceMapper() {}

    public static List<SchemaReference> toModel(List<ReferenceDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        return dtos.stream().map(d -> new SchemaReference(d.targetNodeId(),
                ReferenceType.valueOf(d.type()), d.forward())).toList();
    }
}
