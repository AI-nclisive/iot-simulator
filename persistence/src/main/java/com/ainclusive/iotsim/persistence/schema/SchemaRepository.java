package com.ainclusive.iotsim.persistence.schema;

import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Stores versioned protocol-neutral schemas for a data-source. */
public interface SchemaRepository {

    /** The schema version currently referenced by the data-source, if any. */
    Optional<SchemaWithNodes> findCurrent(String dataSourceId);

    /** A specific schema version for a data-source, used to look up a recording's schema (IS-137). */
    default Optional<SchemaWithNodes> findByVersion(String dataSourceId, int version) {
        return Optional.empty();
    }

    /**
     * Persists a new schema version and points the data-source at it. Atomic.
     */
    SchemaWithNodes saveNewVersion(String dataSourceId, List<SchemaNode> nodes);

    /**
     * Returns the VARIABLE node count for each dataSourceId in the given set.
     * Missing ids (no schema) map to 0. (IS-149)
     * Default implementation delegates to {@link #findCurrent} — only for tests; production
     * code uses the optimised {@link JooqSchemaRepository} override.
     */
    default Map<String, Integer> countVariableNodesBySource(Collection<String> dataSourceIds) {
        if (dataSourceIds == null || dataSourceIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> counts = new java.util.HashMap<>();
        for (String id : dataSourceIds) {
            int n = findCurrent(id)
                    .map(s -> (int) s.nodes().stream()
                            .filter(node -> com.ainclusive.iotsim.protocolmodel.NodeKind.VARIABLE
                                    .equals(node.kind()))
                            .count())
                    .orElse(0);
            counts.put(id, n);
        }
        return counts;
    }
}
