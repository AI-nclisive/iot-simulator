package com.ainclusive.iotsim.persistence.schema;

import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.util.List;
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
}
