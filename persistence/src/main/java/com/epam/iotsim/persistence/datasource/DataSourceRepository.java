package com.epam.iotsim.persistence.datasource;

import java.util.List;
import java.util.Optional;

/** CRUD over {@code data_sources} with optimistic concurrency. */
public interface DataSourceRepository {

    DataSourceRow insert(String projectId, String name, String protocol, String basis,
            String endpointJson, String runtimeConfigJson, String createdBy);

    List<DataSourceRow> findByProject(String projectId);

    Optional<DataSourceRow> findById(String id);

    Optional<DataSourceRow> update(String id, String name, String endpointJson,
            String runtimeConfigJson, boolean enabled, long expectedVersion);

    boolean deleteById(String id);
}
