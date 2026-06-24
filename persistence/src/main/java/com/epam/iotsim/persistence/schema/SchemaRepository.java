package com.epam.iotsim.persistence.schema;

import com.epam.iotsim.protocolmodel.SchemaNode;
import java.util.List;
import java.util.Optional;

/** Stores versioned protocol-neutral schemas for a data-source. */
public interface SchemaRepository {

    /** The schema version currently referenced by the data-source, if any. */
    Optional<SchemaWithNodes> findCurrent(String dataSourceId);

    /**
     * Persists a new schema version and points the data-source at it. Atomic.
     */
    SchemaWithNodes saveNewVersion(String dataSourceId, List<SchemaNode> nodes);
}
