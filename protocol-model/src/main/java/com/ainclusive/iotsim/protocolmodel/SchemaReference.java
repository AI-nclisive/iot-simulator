package com.ainclusive.iotsim.protocolmodel;

import java.util.Objects;

/** A directed, typed link between two nodes in the same schema. */
public record SchemaReference(String targetNodeId, ReferenceType type, boolean forward) {
    public SchemaReference {
        Objects.requireNonNull(targetNodeId, "targetNodeId");
        Objects.requireNonNull(type, "type");
    }
}
