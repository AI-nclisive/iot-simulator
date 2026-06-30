package com.ainclusive.iotsim.domain.datasource;

import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.secret.ConnectionCredentials;
import com.ainclusive.iotsim.platform.secret.CredentialStore;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Data-source lifecycle and runtime control (backend-specs/03 & 05). */
@Service
public class DataSourceService {

    private final DataSourceRepository dataSources;
    private final ProjectRepository projects;
    private final SchemaRepository schemas;
    private final RuntimeController runtime;
    private final CredentialStore credentials;

    public DataSourceService(DataSourceRepository dataSources, ProjectRepository projects,
            SchemaRepository schemas, RuntimeController runtime, CredentialStore credentials) {
        this.dataSources = dataSources;
        this.projects = projects;
        this.schemas = schemas;
        this.runtime = runtime;
        this.credentials = credentials;
    }

    public DataSource create(String projectId, String name, String protocol, String basis,
            String endpoint, String runtimeConfig, ConnectionCredentials connectionCredentials, String actor) {
        requireProject(projectId);
        // Validate enum inputs early (invalid -> IllegalArgumentException -> 400).
        Protocol.valueOf(protocol);
        SourceBasis.valueOf(basis);
        DataSourceRow row = dataSources.insert(projectId, name, protocol, basis, endpoint, runtimeConfig, actor);
        applyCredentials(row.id(), connectionCredentials);
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
            String runtimeConfig, Boolean enabled, ConnectionCredentials connectionCredentials,
            long expectedVersion) {
        DataSourceRow existing = requireRow(projectId, id);
        String newName = name != null ? name : existing.name();
        String newEndpoint = endpoint != null ? endpoint : existing.endpoint();
        String newRuntimeConfig = runtimeConfig != null ? runtimeConfig : existing.runtimeConfig();
        boolean newEnabled = enabled != null ? enabled : existing.enabled();
        DataSourceRow updated = dataSources.update(id, newName, newEndpoint, newRuntimeConfig, newEnabled, expectedVersion)
                .orElseThrow(() -> new ConcurrencyConflictException("DataSource", id, expectedVersion));
        // Apply credentials only after the version check passes, so a stale write touches no secret.
        applyCredentials(id, connectionCredentials);
        return map(updated);
    }

    /** Clears any held connection credentials for the source ("clear value" in the Credential Handling UI). */
    public DataSource clearCredentials(String projectId, String id) {
        DataSourceRow row = requireRow(projectId, id);
        credentials.clear(id);
        return map(row);
    }

    public void delete(String projectId, String id) {
        requireRow(projectId, id);
        runtime.stop(id);
        dataSources.deleteById(id);
        credentials.clear(id);
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

    /**
     * Creates a deep copy of a data-source within the same project.
     * The copy has a new ID, name {@code "<original name> (copy)"}, {@code enabled=false},
     * and {@code runtimeState=STOPPED}. If the source has a schema, its nodes are
     * copied to a new schema version on the copy (starting at version 1).
     * Returns 404 if the source or project is not found.
     */
    @Transactional
    public DataSource duplicate(String projectId, String id, String actor) {
        requireProject(projectId);
        DataSourceRow source = requireRow(projectId, id);
        String copyName = source.name() + " (copy)";
        DataSourceRow initialCopy = dataSources.duplicate(source.id(), copyName, actor)
                .orElseThrow(() -> new ResourceNotFoundException("DataSource", id));
        // Copy schema nodes if the source has a schema.
        Optional<SchemaWithNodes> sourceSchema = schemas.findCurrent(source.id());
        if (sourceSchema.isPresent() && !sourceSchema.get().nodes().isEmpty()) {
            schemas.saveNewVersion(initialCopy.id(), sourceSchema.get().nodes());
            // Re-fetch so schemaId/schemaVersion are populated after saveNewVersion updates the row.
            String copyId = initialCopy.id();
            return map(dataSources.findById(copyId)
                    .orElseThrow(() -> new ResourceNotFoundException("DataSource", copyId)));
        }
        return map(initialCopy);
    }

    /**
     * Stores or clears credentials for the source. {@code null} leaves them
     * unchanged; {@code ANONYMOUS} clears them; otherwise they are stored
     * session-only (never persisted in the row — backend-specs/08).
     */
    private void applyCredentials(String id, ConnectionCredentials connectionCredentials) {
        if (connectionCredentials == null) {
            return;
        }
        if (connectionCredentials.mode() == ConnectionCredentials.Mode.ANONYMOUS) {
            credentials.clear(id);
        } else {
            credentials.put(id, connectionCredentials);
        }
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
                credentials.has(r.id()) ? CredentialState.SESSION_ONLY : CredentialState.MISSING,
                r.createdAt().toInstant(),
                r.updatedAt().toInstant(),
                r.createdBy(),
                r.version());
    }
}
