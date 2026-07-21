package com.ainclusive.iotsim.persistence.manualschema;

import java.time.OffsetDateTime;

/**
 * Persistence-level projection of a {@code manual_schemas} row (IS-171).
 *
 * <p>{@code nodesJson} is a JSON array shaped like {@code List<SchemaNode>}, stored and
 * read back as a raw string — the same convention {@code RecordingRow.schemaNodesJson}
 * uses — so (de)serialization to {@code SchemaNode} stays a domain-layer concern.
 */
public record ManualSchemaRow(
        String id,
        String projectId,
        String protocol,
        String name,
        String description,
        String nodesJson,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        long version) {
}
