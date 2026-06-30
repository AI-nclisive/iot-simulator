package com.ainclusive.iotsim.api.datasource;

import com.ainclusive.iotsim.api.error.PreconditionRequiredException;
import com.ainclusive.iotsim.api.support.ConnectionConfigRequest;
import com.ainclusive.iotsim.api.support.CredentialRequests;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Data-sources under a project. Mirrors the Projects resource conventions:
 * /api/v1, ETag / If-Match optimistic concurrency, start/stop runtime control.
 * See backend-specs/05_API_CONTRACT.md.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/data-sources")
public class DataSourceController {

    private final DataSourceService dataSources;

    public DataSourceController(DataSourceService dataSources) {
        this.dataSources = dataSources;
    }

    @GetMapping
    public List<DataSourceResponse> list(@PathVariable String projectId) {
        return dataSources.list(projectId).stream().map(DataSourceResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<DataSourceResponse> create(
            @PathVariable String projectId, @RequestBody CreateDataSourceRequest req) {
        require(req != null && notBlank(req.name()), "name is required");
        require(notBlank(req.protocol()), "protocol is required");
        require(notBlank(req.basis()), "basis is required");
        DataSource ds = dataSources.create(
                projectId, req.name(), req.protocol(), req.basis(),
                req.endpoint(), req.runtimeConfig(), CredentialRequests.toCredentials(req.connectionConfig()), "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/data-sources/" + ds.id()))
                .eTag(etag(ds.version()))
                .body(DataSourceResponse.from(ds));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DataSourceResponse> get(@PathVariable String projectId, @PathVariable String id) {
        DataSource ds = dataSources.get(projectId, id);
        return ResponseEntity.ok().eTag(etag(ds.version())).body(DataSourceResponse.from(ds));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DataSourceResponse> update(
            @PathVariable String projectId, @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody UpdateDataSourceRequest req) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException("If-Match header with the current version is required");
        }
        DataSource ds = dataSources.update(
                projectId, id, req.name(), req.endpoint(), req.runtimeConfig(),
                req.enabled(), CredentialRequests.toCredentials(req.connectionConfig()), parseVersion(ifMatch));
        return ResponseEntity.ok().eTag(etag(ds.version())).body(DataSourceResponse.from(ds));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String projectId, @PathVariable String id) {
        dataSources.delete(projectId, id);
        return ResponseEntity.noContent().build();
    }

    /** Clears any held connection credentials ("clear value" in the Credential Handling surface). */
    @DeleteMapping("/{id}/credentials")
    public ResponseEntity<DataSourceResponse> clearCredentials(
            @PathVariable String projectId, @PathVariable String id) {
        DataSource ds = dataSources.clearCredentials(projectId, id);
        return ResponseEntity.ok().eTag(etag(ds.version())).body(DataSourceResponse.from(ds));
    }

    /**
     * Deep-copies an existing data source within the same project.
     * The copy gets a new ID, name {@code "<original name> (copy)"}, enabled=false, and STOPPED state.
     * Schema nodes are copied when present. Returns 201 with the new resource. See IS-066.
     */
    @PostMapping("/{id}/duplicate")
    public ResponseEntity<DataSourceResponse> duplicate(@PathVariable String projectId, @PathVariable String id) {
        DataSource ds = dataSources.duplicate(projectId, id, "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/data-sources/" + ds.id()))
                .eTag(etag(ds.version()))
                .body(DataSourceResponse.from(ds));
    }

    @PostMapping("/{id}/start")
    public ResponseEntity<DataSourceResponse> start(@PathVariable String projectId, @PathVariable String id) {
        DataSource ds = dataSources.start(projectId, id);
        return ResponseEntity.ok().eTag(etag(ds.version())).body(DataSourceResponse.from(ds));
    }

    @PostMapping("/{id}/stop")
    public ResponseEntity<DataSourceResponse> stop(@PathVariable String projectId, @PathVariable String id) {
        DataSource ds = dataSources.stop(projectId, id);
        return ResponseEntity.ok().eTag(etag(ds.version())).body(DataSourceResponse.from(ds));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    private static long parseVersion(String ifMatch) {
        String v = ifMatch.trim();
        if (v.startsWith("W/")) {
            v = v.substring(2);
        }
        v = v.replace("\"", "").trim();
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid If-Match version: " + ifMatch);
        }
    }

    public record CreateDataSourceRequest(
            String name, String protocol, String basis, String endpoint, String runtimeConfig,
            ConnectionConfigRequest connectionConfig) {}

    public record UpdateDataSourceRequest(
            String name, String endpoint, String runtimeConfig, Boolean enabled,
            ConnectionConfigRequest connectionConfig) {}

    public record DataSourceResponse(
            String id, String projectId, String name, String protocol, String basis,
            String schemaId, Integer schemaVersion, String endpoint, String runtimeConfig,
            boolean enabled, String runtimeState, String credentialState,
            Instant createdAt, Instant updatedAt, String createdBy, long version) {

        public static DataSourceResponse from(DataSource d) {
            return new DataSourceResponse(
                    d.id(), d.projectId(), d.name(), d.protocol().name(), d.basis().name(),
                    d.schemaId(), d.schemaVersion(), d.endpoint(), d.runtimeConfig(),
                    d.enabled(), d.runtimeState().name(), d.credentialState().name(),
                    d.createdAt(), d.updatedAt(), d.createdBy(), d.version());
        }
    }
}
