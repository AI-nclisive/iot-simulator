package com.ainclusive.iotsim.persistence.datasource;

import java.util.List;
import java.util.Optional;

/** CRUD over {@code data_sources} with optimistic concurrency. */
public interface DataSourceRepository {

    DataSourceRow insert(String projectId, String name, String protocol, String basis,
            String endpointJson, String runtimeConfigJson, String createdBy);

    /**
     * Creates a copy of an existing data-source row under the same project.
     * The copy gets a new ID, the supplied name, {@code enabled=false}, and version 0.
     * Returns an empty Optional if {@code sourceId} does not exist.
     */
    Optional<DataSourceRow> duplicate(String sourceId, String newName, String createdBy);

    List<DataSourceRow> findByProject(String projectId);

    Optional<DataSourceRow> findById(String id);

    Optional<DataSourceRow> update(String id, String name, String endpointJson,
            String runtimeConfigJson, boolean enabled, long expectedVersion);

    boolean deleteById(String id);
}
