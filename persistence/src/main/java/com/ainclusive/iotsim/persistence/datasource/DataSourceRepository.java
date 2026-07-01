package com.ainclusive.iotsim.persistence.datasource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** CRUD over {@code data_sources} with optimistic concurrency. */
public interface DataSourceRepository {

    DataSourceRow insert(String projectId, String name, String protocol, String basis,
            String endpoint, String runtimeConfigJson, String createdBy);

    /**
     * Creates a copy of an existing data-source row under the same project.
     * The copy gets a new ID, the supplied name, {@code enabled=false}, and version 0.
     * Returns an empty Optional if {@code sourceId} does not exist.
     */
    Optional<DataSourceRow> duplicate(String sourceId, String newName, String createdBy);

    /** All data sources across all projects, newest first. Host-wide (used for port-uniqueness). */
    default List<DataSourceRow> findAll() {
        return List.of();
    }

    List<DataSourceRow> findByProject(String projectId);

    /** Cursor-paged list with optional protocol filter (IS-074). Sort: {@code created_at DESC, id DESC}. */
    List<DataSourceRow> findByProjectPaged(String projectId, String protocol,
            OffsetDateTime afterAt, String afterId, int limit);

    Optional<DataSourceRow> findById(String id);

    Optional<DataSourceRow> update(String id, String name, String endpoint,
            String runtimeConfigJson, boolean enabled, long expectedVersion);

    boolean deleteById(String id);
}
