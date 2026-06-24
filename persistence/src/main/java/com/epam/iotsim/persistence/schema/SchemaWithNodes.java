package com.epam.iotsim.persistence.schema;

import com.epam.iotsim.protocolmodel.SchemaNode;
import java.time.OffsetDateTime;
import java.util.List;

/** A persisted schema version and its nodes. */
public record SchemaWithNodes(
        String id,
        String dataSourceId,
        int version,
        OffsetDateTime createdAt,
        List<SchemaNode> nodes) {
}
