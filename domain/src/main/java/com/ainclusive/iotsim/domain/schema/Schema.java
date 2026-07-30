package com.ainclusive.iotsim.domain.schema;

import com.ainclusive.iotsim.protocolmodel.NativeTypeCatalog;
import com.ainclusive.iotsim.protocolmodel.NativeTypeDefinition;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.time.Instant;
import java.util.List;

/** A versioned protocol-neutral schema for a data-source. */
public record Schema(
        String id,
        String dataSourceId,
        int version,
        List<SchemaNode> nodes,
        Instant createdAt) {

    /** Additive catalog view of native declarations retained by this schema version. */
    public List<NativeTypeDefinition> typeDefinitions() {
        return NativeTypeCatalog.fromSchemaNodes(nodes);
    }
}
