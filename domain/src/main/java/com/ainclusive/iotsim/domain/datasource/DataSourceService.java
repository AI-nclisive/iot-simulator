package com.ainclusive.iotsim.domain.datasource;

import com.ainclusive.iotsim.domain.activityevent.ActivityEventService;
import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.PortInUseException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.domain.support.PageCursor;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.secret.ConnectionCredentials;
import com.ainclusive.iotsim.platform.secret.CredentialStore;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Data-source lifecycle and runtime control (backend-specs/03 & 05). */
@Service
public class DataSourceService {

    private final DataSourceRepository dataSources;
    private final ProjectRepository projects;
    private final SchemaRepository schemas;
    private final RuntimeController runtime;
    private final CredentialStore credentials;
    private final ObjectMapper json;
    private final String advertisedHost;
    private final ActivityEventService activity;

    public DataSourceService(DataSourceRepository dataSources, ProjectRepository projects,
            SchemaRepository schemas, RuntimeController runtime, CredentialStore credentials,
            ObjectMapper json,
            @org.springframework.beans.factory.annotation.Value(
                    "${iotsim.simulator.advertised-host:localhost}") String advertisedHost,
            ActivityEventService activity) {
        this.dataSources = dataSources;
        this.projects = projects;
        this.schemas = schemas;
        this.runtime = runtime;
        this.credentials = credentials;
        this.json = json;
        this.advertisedHost = advertisedHost;
        this.activity = activity;
    }

    /**
     * Creates a new data source. If {@code initialNodes} is non-null and non-empty the nodes
     * are saved as schema version 1 atomically with the insert (IS-067: create from import /
     * prepared data).
     */
    @Transactional
    public DataSource create(String projectId, String name, String protocol, String basis,
            Integer simulatorPort, String realDeviceEndpoint, String runtimeConfig, String securityConfig,
            ConnectionCredentials connectionCredentials, List<SchemaNode> initialNodes, String actor) {
        requireProject(projectId);
        // Validate enum inputs early (invalid -> IllegalArgumentException -> 400).
        Protocol parsedProtocol = Protocol.valueOf(protocol);
        SourceBasis.valueOf(basis);
        requireValidJson(runtimeConfig, "runtimeConfig");
        String storedSecurity = EndpointSecurityCodec.normalizeForStorage(securityConfig);
        int port = simulatorPort != null
                ? validatePort(simulatorPort)
                : SimulatorUrl.defaultPort(parsedProtocol);
        DataSourceRow row = dataSources.insert(
                projectId, name, protocol, basis, port, realDeviceEndpoint, runtimeConfig,
                storedSecurity, actor);
        applyCredentials(row.id(), connectionCredentials);
        DataSource result;
        if (initialNodes != null && !initialNodes.isEmpty()) {
            schemas.saveNewVersion(row.id(), initialNodes);
            result = map(dataSources.findById(row.id())
                    .orElseThrow(() -> new ResourceNotFoundException("DataSource", row.id())));
        } else {
            result = map(row);
        }
        activity.emit(projectId, actor, "create", "data_source", row.id());
        return result;
    }

    public List<DataSource> list(String projectId) {
        requireProject(projectId);
        return dataSources.findByProject(projectId).stream().map(this::map).toList();
    }

    public Page<DataSource> listPaged(String projectId, String protocol, String cursor, Integer limit) {
        requireProject(projectId);
        int size = PageCursor.clamp(limit);
        PageCursor.Parts after = PageCursor.decode(cursor);
        OffsetDateTime afterAt = after != null ? after.at() : null;
        String afterId = after != null ? after.id() : null;
        List<DataSourceRow> rows = dataSources.findByProjectPaged(projectId, protocol, afterAt, afterId, size + 1);
        String nextCursor = null;
        if (rows.size() > size) {
            rows = rows.subList(0, size);
            DataSourceRow last = rows.get(rows.size() - 1);
            nextCursor = PageCursor.encode(last.createdAt(), last.id());
        }
        return new Page<>(rows.stream().map(this::map).toList(), nextCursor, size);
    }

    public DataSource get(String projectId, String id) {
        return map(requireRow(projectId, id));
    }

    public DataSource update(String projectId, String id, String name, Integer simulatorPort,
            String realDeviceEndpoint, String runtimeConfig, String securityConfig, Boolean enabled,
            ConnectionCredentials connectionCredentials, long expectedVersion) {
        DataSourceRow existing = requireRow(projectId, id);
        requireValidJson(runtimeConfig, "runtimeConfig");
        String newName = name != null ? name : existing.name();
        // A null simulatorPort keeps the persisted port unchanged; an explicit value is validated.
        int newPort = simulatorPort != null ? validatePort(simulatorPort) : existing.simulatorPort();
        String newEndpoint = realDeviceEndpoint != null ? realDeviceEndpoint : existing.realDeviceEndpoint();
        String newRuntimeConfig = runtimeConfig != null ? runtimeConfig : existing.runtimeConfig();
        // null leaves the persisted security config unchanged; an explicit value is normalised + hashed.
        String newSecurity = securityConfig != null
                ? EndpointSecurityCodec.normalizeForStorage(securityConfig)
                : existing.securityConfig();
        boolean newEnabled = enabled != null ? enabled : existing.enabled();
        DataSourceRow updated = dataSources.update(
                id, newName, newPort, newEndpoint, newRuntimeConfig, newSecurity, newEnabled, expectedVersion)
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

    @Transactional
    public void delete(String projectId, String id, String actor) {
        requireRow(projectId, id);
        runtime.stop(id);
        dataSources.deleteById(id);
        credentials.clear(id);
        activity.emit(projectId, actor, "delete", "data_source", id);
    }

    public DataSource start(String projectId, String id, String actor) {
        DataSourceRow row = requireRow(projectId, id);
        int port = row.simulatorPort();
        for (DataSourceRow other : dataSources.findAll()) {
            if (!other.id().equals(id)
                    && "RUNNING".equals(runtime.state(other.id()))
                    && other.simulatorPort() == port) {
                throw new PortInUseException(port, other.id());
            }
        }
        runtime.start(id, RuntimeStartSpecs.of(schemas, row));
        activity.emit(projectId, actor, "start", "data_source", id);
        return map(row);
    }

    public DataSource stop(String projectId, String id, String actor) {
        DataSourceRow row = requireRow(projectId, id);
        runtime.stop(id);
        activity.emit(projectId, actor, "stop", "data_source", id);
        return map(row);
    }

    public void injectFault(String projectId, String id, String kind, String layer,
            boolean active, Map<String, String> params) {
        requireRow(projectId, id);
        runtime.injectFault(id, kind, layer, active, params);
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

    /** Rejects a malformed jsonb field up front so it surfaces as 400, not a 500 from the DB driver. */
    private void requireValidJson(String value, String field) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            json.readTree(value);
        } catch (JacksonException e) {
            throw new IllegalArgumentException(field + " must be valid JSON");
        }
    }

    private DataSourceRow requireRow(String projectId, String id) {
        return dataSources.findById(id)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("DataSource", id));
    }

    /** Validates an explicit simulator port; the port range is 1..65535. */
    private static int validatePort(int port) {
        if (port < 1 || port > 65535) {
            throw new IllegalArgumentException("simulatorPort must be between 1 and 65535, got: " + port);
        }
        return port;
    }

    private DataSource map(DataSourceRow r) {
        Protocol protocol = Protocol.valueOf(r.protocol());
        String serveUrl = SimulatorUrl.of(protocol, advertisedHost, r.simulatorPort());
        return new DataSource(
                r.id(),
                r.projectId(),
                r.name(),
                protocol,
                SourceBasis.valueOf(r.basis()),
                r.schemaId(),
                r.schemaVersion(),
                r.simulatorPort(),
                r.realDeviceEndpoint(),
                r.runtimeConfig(),
                r.securityConfig(),
                r.enabled(),
                RuntimeState.valueOf(runtime.state(r.id())),
                credentials.has(r.id()) ? CredentialState.SESSION_ONLY : CredentialState.MISSING,
                serveUrl,
                r.createdAt().toInstant(),
                r.updatedAt().toInstant(),
                r.createdBy(),
                r.version());
    }
}
