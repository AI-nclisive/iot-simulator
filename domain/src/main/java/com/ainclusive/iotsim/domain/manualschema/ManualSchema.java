package com.ainclusive.iotsim.domain.manualschema;

import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.time.Instant;
import java.util.List;

/**
 * A reusable, project-scoped, protocol-scoped structure artifact (folders + typed
 * variables, no values) — not bound to any data-source; the parameter-set counterpart
 * to {@code Recording}. See backend-specs/03_DOMAIN_MODEL.md §ManualSchema.
 *
 * <p>Save model is save-in-place or save-as-new (no monotonic version chain like
 * {@code Schema}); {@code version} exists only for optimistic concurrency on save-in-place.
 */
public record ManualSchema(
        String id,
        String projectId,
        String protocol,
        String name,
        String description,
        List<SchemaNode> nodes,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        long version) {
}
