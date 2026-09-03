package com.ainclusive.iotsim.persistence.datasource;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** CRUD over {@code data_sources} with optimistic concurrency. */
public interface DataSourceRepository {

    DataSourceRow insert(String projectId, String name, String protocol, String basis,
            int simulatorPort, String realDeviceEndpoint, String runtimeConfigJson,
            String securityConfigJson, String createdBy);

    default DataSourceRow insert(String projectId, String name, String protocol, String basis,
            int simulatorPort, String realDeviceEndpoint, Integer realDeviceUnitId, String runtimeConfigJson,
            String securityConfigJson, String createdBy) {
        if (realDeviceUnitId != null) {
            throw new UnsupportedOperationException("repository must implement realDeviceUnitId persistence");
        }
        return insert(projectId, name, protocol, basis, simulatorPort, realDeviceEndpoint,
                runtimeConfigJson, securityConfigJson, createdBy);
    }

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

    Optional<DataSourceRow> update(String id, String name, int simulatorPort,
            String realDeviceEndpoint, String runtimeConfigJson, String securityConfigJson,
            boolean enabled, long expectedVersion);

    default Optional<DataSourceRow> update(String id, String name, int simulatorPort,
            String realDeviceEndpoint, Integer realDeviceUnitId, String runtimeConfigJson,
            String securityConfigJson, boolean enabled, long expectedVersion) {
        if (realDeviceUnitId != null) {
            throw new UnsupportedOperationException("repository must implement realDeviceUnitId persistence");
        }
        return update(id, name, simulatorPort, realDeviceEndpoint, runtimeConfigJson,
                securityConfigJson, enabled, expectedVersion);
    }

    /** Patches only the {@code runtime_config} column (best-effort, no version check). */
    default void saveRuntimeConfig(String id, String runtimeConfigJson) {}

    boolean deleteById(String id);
}
