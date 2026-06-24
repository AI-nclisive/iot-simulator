package com.ainclusive.iotsim.domain.datasource;

import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import java.util.List;
import org.springframework.stereotype.Service;

/** Data-source lifecycle and runtime control (backend-specs/03 & 05). */
@Service
public class DataSourceService {

    private final DataSourceRepository dataSources;
    private final ProjectRepository projects;
    private final SchemaRepository schemas;
    private final RuntimeController runtime;

    public DataSourceService(DataSourceRepository dataSources, ProjectRepository projects,
            SchemaRepository schemas, RuntimeController runtime) {
        this.dataSources = dataSources;
        this.projects = projects;
        this.schemas = schemas;
        this.runtime = runtime;
    }

    public DataSource create(String projectId, String name, String protocol, String basis,
            String endpoint, String runtimeConfig, String actor) {
        requireProject(projectId);
        // Validate enum inputs early (invalid -> IllegalArgumentException -> 400).
        Protocol.valueOf(protocol);
        SourceBasis.valueOf(basis);
        DataSourceRow row = dataSources.insert(projectId, name, protocol, basis, endpoint, runtimeConfig, actor);
        return map(row);
    }

    public List<DataSource> list(String projectId) {
        requireProject(projectId);
        return dataSources.findByProject(projectId).stream().map(this::map).toList();
    }

    public DataSource get(String projectId, String id) {
        return map(requireRow(projectId, id));
    }

    public DataSource update(String projectId, String id, String name, String endpoint,
            String runtimeConfig, Boolean enabled, long expectedVersion) {
        DataSourceRow existing = requireRow(projectId, id);
        String newName = name != null ? name : existing.name();
        String newEndpoint = endpoint != null ? endpoint : existing.endpoint();
        String newRuntimeConfig = runtimeConfig != null ? runtimeConfig : existing.runtimeConfig();
        boolean newEnabled = enabled != null ? enabled : existing.enabled();
        return dataSources.update(id, newName, newEndpoint, newRuntimeConfig, newEnabled, expectedVersion)
                .map(this::map)
                .orElseThrow(() -> new ConcurrencyConflictException("DataSource", id, expectedVersion));
    }

    public void delete(String projectId, String id) {
        requireRow(projectId, id);
        runtime.stop(id);
        dataSources.deleteById(id);
    }

    public DataSource start(String projectId, String id) {
        DataSourceRow row = requireRow(projectId, id);
        runtime.start(id, RuntimeStartSpecs.of(schemas, row));
        return map(row);
    }

    public DataSource stop(String projectId, String id) {
        DataSourceRow row = requireRow(projectId, id);
        runtime.stop(id);
        return map(row);
    }

    private void requireProject(String projectId) {
        if (projects.findById(projectId).isEmpty()) {
            throw new ResourceNotFoundException("Project", projectId);
        }
    }

    private DataSourceRow requireRow(String projectId, String id) {
        return dataSources.findById(id)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("DataSource", id));
    }

    private DataSource map(DataSourceRow r) {
        return new DataSource(
                r.id(),
                r.projectId(),
                r.name(),
                Protocol.valueOf(r.protocol()),
                SourceBasis.valueOf(r.basis()),
                r.schemaId(),
                r.schemaVersion(),
                r.endpoint(),
                r.runtimeConfig(),
                r.enabled(),
                RuntimeState.valueOf(runtime.state(r.id())),
                r.createdAt().toInstant(),
                r.updatedAt().toInstant(),
                r.createdBy(),
                r.version());
    }
}
