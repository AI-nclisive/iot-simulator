package com.ainclusive.iotsim.api.schema;

import com.ainclusive.iotsim.protocolmodel.SchemaReference;

/** REST representation of a directed typed schema reference. */
public record ReferenceDto(String targetNodeId, String type, boolean forward) {
    public static ReferenceDto from(SchemaReference reference) {
        return new ReferenceDto(reference.targetNodeId(), reference.type().name(), reference.forward());
    }
}
