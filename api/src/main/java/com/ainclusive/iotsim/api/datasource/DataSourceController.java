package com.ainclusive.iotsim.api.datasource;

import com.ainclusive.iotsim.api.error.PreconditionRequiredException;
import com.ainclusive.iotsim.api.scan.ScanController;
import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.api.support.ConnectionConfigRequest;
import com.ainclusive.iotsim.api.support.CredentialRequests;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.datasource.SecurityConfigRedactor;
import com.ainclusive.iotsim.domain.scan.ScanJob;
import com.ainclusive.iotsim.domain.scan.ScanService;
import com.ainclusive.iotsim.domain.schema.SchemaService;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Data-sources under a project. Mirrors the Projects resource conventions:
 * /api/v1, ETag / If-Match optimistic concurrency, start/stop runtime control.
 * See backend-specs/05_API_CONTRACT.md.
 *
 * <p>Authorization (IS-077, backend-specs/08 §Authorization):
 * <ul>
 *   <li>List / get — {@link Permission#OBSERVE} (user + admin).
 *   <li>Create / update / delete / duplicate / credentials — {@link Permission#SOURCE_EDIT} (admin).
 *   <li>Stop — {@link Permission#SOURCE_STOP} (user + admin).
 * </ul>
 */
@RestController
@Tag(name = "Data Sources")
@RequestMapping("/api/v1/projects/{projectId}/data-sources")
public class DataSourceController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";
    private static final String SOURCE_EDIT =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).SOURCE_EDIT)";
    private static final String SOURCE_STOP =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).SOURCE_STOP)";

    private final DataSourceService dataSources;
    private final SchemaService schemas;
    private final ScanService scans;

    public DataSourceController(DataSourceService dataSources, SchemaService schemas, ScanService scans) {
        this.dataSources = dataSources;
        this.schemas = schemas;
        this.scans = scans;
    }

    @Operation(
            summary = "List data sources",
            description = "Returns data sources in the project, optionally filtered by protocol,"
                    + " using cursor-based pagination.")
    @GetMapping
    @PreAuthorize(OBSERVE)
    public Page<DataSourceResponse> list(
            @PathVariable String projectId,
            @RequestParam(required = false) String protocol,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        Page<DataSource> page = dataSources.listPaged(projectId, protocol, cursor, limit);
        List<DataSource> items = page.items();
        if (items.isEmpty()) {
            return page.map(ds -> DataSourceResponse.from(ds, 0));
        }
        List<String> ids = items.stream().map(DataSource::id).collect(Collectors.toList());
        Map<String, Integer> counts = schemas.countVariableNodes(ids);
        return page.map(ds -> DataSourceResponse.from(ds, counts.getOrDefault(ds.id(), 0)));
    }

    @Operation(
            summary = "Create a data source",
            description = "Creates a data source in the project, optionally with an initial schema."
                    + " Returns 201 Created with a Location header and the current ETag.")
    @PostMapping
    @PreAuthorize(SOURCE_EDIT)
    public ResponseEntity<DataSourceResponse> create(
            @PathVariable String projectId, @RequestBody CreateDataSourceRequest req) {
        require(req != null && notBlank(req.name()), "name is required");
        require(notBlank(req.protocol()), "protocol is required");
        require(notBlank(req.basis()), "basis is required");
        List<SchemaNode> initialNodes = req.initialSchema() != null && !req.initialSchema().isEmpty()
                ? toNodes(req.initialSchema())
                : null;
        String runtimeConfig = req.runtimeConfig();
        DataSource ds = dataSources.create(
                projectId, req.name(), req.protocol(), req.basis(),
                req.simulatorPort(), req.realDeviceEndpoint(), runtimeConfig, req.securityConfig(),
                CredentialRequests.toCredentials(req.connectionConfig()), initialNodes, "local");
        int paramCount = schemas.countVariableNodes(ds.id());
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/data-sources/" + ds.id()))
                .eTag(etag(ds.version()))
                .body(DataSourceResponse.from(ds, paramCount));
    }

    @Operation(
            summary = "Get a data source",
            description = "Returns a single data source by id, with its current version as the ETag.")
    @GetMapping("/{id}")
    @PreAuthorize(OBSERVE)
    public ResponseEntity<DataSourceResponse> get(@PathVariable String projectId, @PathVariable String id) {
        DataSource ds = dataSources.get(projectId, id);
        int paramCount = schemas.countVariableNodes(ds.id());
        return ResponseEntity.ok().eTag(etag(ds.version())).body(DataSourceResponse.from(ds, paramCount));
    }

    @Operation(
            summary = "Update a data source",
            description = "Updates a data source's mutable fields. Requires an If-Match header carrying the"
                    + " current version for optimistic concurrency; returns the new ETag.")
    @PutMapping("/{id}")
    @PreAuthorize(SOURCE_EDIT)
    public ResponseEntity<DataSourceResponse> update(
            @PathVariable String projectId, @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody UpdateDataSourceRequest req) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException("If-Match header with the current version is required");
        }
        DataSource ds = dataSources.update(
                projectId, id, req.name(), req.simulatorPort(), req.realDeviceEndpoint(),
                req.runtimeConfig(), req.securityConfig(), req.enabled(),
                CredentialRequests.toCredentials(req.connectionConfig()), parseVersion(ifMatch));
        int paramCount = schemas.countVariableNodes(ds.id());
        return ResponseEntity.ok().eTag(etag(ds.version())).body(DataSourceResponse.from(ds, paramCount));
    }

    @Operation(
            summary = "Delete a data source",
            description = "Deletes a data source and returns 204 No Content.")
    @DeleteMapping("/{id}")
    @PreAuthorize(SOURCE_EDIT)
    public ResponseEntity<Void> delete(@PathVariable String projectId, @PathVariable String id) {
        dataSources.delete(projectId, id, "local");
        return ResponseEntity.noContent().build();
    }

    /** Clears any held connection credentials ("clear value" in the Credential Handling surface). */
    @Operation(
            summary = "Clear stored credentials",
            description = "Clears any stored connection credentials for the data source and returns"
                    + " its updated state.")
    @DeleteMapping("/{id}/credentials")
    @PreAuthorize(SOURCE_EDIT)
    public ResponseEntity<DataSourceResponse> clearCredentials(
            @PathVariable String projectId, @PathVariable String id) {
        DataSource ds = dataSources.clearCredentials(projectId, id);
        int paramCount = schemas.countVariableNodes(ds.id());
        return ResponseEntity.ok().eTag(etag(ds.version())).body(DataSourceResponse.from(ds, paramCount));
    }

    /**
     * Deep-copies an existing data source within the same project.
     * The copy gets a new ID, name {@code "<original name> (copy)"}, enabled=false, and STOPPED state.
     * Schema nodes are copied when present. Returns 201 with the new resource. See IS-066.
     */
    @Operation(
            summary = "Duplicate a data source",
            description = "Deep-copies the data source within the same project, giving the copy a new id,"
                    + " a \"(copy)\" name, disabled and STOPPED. Returns 201 Created with a Location header.")
    @PostMapping("/{id}/duplicate")
    @PreAuthorize(SOURCE_EDIT)
    public ResponseEntity<DataSourceResponse> duplicate(@PathVariable String projectId, @PathVariable String id) {
        DataSource ds = dataSources.duplicate(projectId, id, "local");
        int paramCount = schemas.countVariableNodes(ds.id());
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/data-sources/" + ds.id()))
                .eTag(etag(ds.version()))
                .body(DataSourceResponse.from(ds, paramCount));
    }

    @Operation(
            summary = "Stop the data source runtime",
            description = "Stops the running runtime worker for the data source and returns its updated"
                    + " runtime state.")
    @PostMapping("/{id}/stop")
    @PreAuthorize(SOURCE_STOP)
    public ResponseEntity<DataSourceResponse> stop(@PathVariable String projectId, @PathVariable String id) {
        DataSource ds = dataSources.stop(projectId, id, "local");
        int paramCount = schemas.countVariableNodes(ds.id());
        return ResponseEntity.ok().eTag(etag(ds.version())).body(DataSourceResponse.from(ds, paramCount));
    }

    /**
     * Re-scans an already-created (basis=SCAN) data source's real endpoint, reusing its
     * stored protocol, endpoint, and connection credentials — no re-entry of connection
     * details needed. Starts an async job just like create-from-scan; poll it via the
     * existing {@code GET /data-sources/scan/{jobId}} endpoint.
     */
    @Operation(
            summary = "Rescan a data source's real endpoint",
            description = "Starts an asynchronous rescan of an existing SCAN-basis data source's real"
                    + " endpoint, reusing its stored connection details. Returns 202 Accepted with a"
                    + " Location header and the job id to poll via the scan job endpoint.")
    @PostMapping("/{id}/rescan")
    @PreAuthorize(SOURCE_EDIT)
    public ResponseEntity<ScanController.StartScanResponse> rescan(
            @PathVariable String projectId, @PathVariable String id) {
        ScanJob job = scans.startRescan(projectId, id);
        URI location = URI.create("/api/v1/projects/" + projectId + "/data-sources/scan/" + job.jobId());
        return ResponseEntity.accepted().location(location)
                .body(new ScanController.StartScanResponse(job.jobId(), job.state()));
    }

    /**
     * Applies a completed rescan job's discovered structure onto the existing data source
     * as a new schema version. Unknown-typed nodes must be addressed via {@code typeResolutions}
     * the same way create-from-scan requires.
     */
    @Operation(
            summary = "Apply a completed rescan",
            description = "Saves a completed rescan job's discovered structure as a new schema version on"
                    + " the existing data source, applying type resolutions for unknown-typed nodes.")
    @PostMapping("/{id}/rescan/{jobId}/apply")
    @PreAuthorize(SOURCE_EDIT)
    public ResponseEntity<DataSourceResponse> applyRescan(
            @PathVariable String projectId, @PathVariable String id, @PathVariable String jobId,
            @RequestBody ApplyRescanRequest req) {
        DataSource ds = scans.applyRescan(projectId, id, jobId,
                ScanController.toResolutions(req.typeResolutions()));
        int paramCount = schemas.countVariableNodes(ds.id());
        return ResponseEntity.ok().eTag(etag(ds.version())).body(DataSourceResponse.from(ds, paramCount));
    }

    private static List<SchemaNode> toNodes(List<NodeDto> dtos) {
        List<SchemaNode> nodes = new ArrayList<>(dtos.size());
        for (NodeDto d : dtos) {
            requireText(d.nodeId(), "nodeId");
            requireText(d.path(), "path");
            requireText(d.name(), "name");
            NodeKind kind = parseEnum(NodeKind.class, d.kind(), "kind");
            DataType dataType = d.dataType() == null ? null : parseEnum(DataType.class, d.dataType(), "dataType");
            ValueRank valueRank =
                    d.valueRank() == null ? null : parseEnum(ValueRank.class, d.valueRank(), "valueRank");
            Access access = d.access() == null ? null : parseEnum(Access.class, d.access(), "access");
            if (kind == NodeKind.VARIABLE && (dataType == null || valueRank == null || access == null)) {
                throw new IllegalArgumentException(
                        "variable node '" + d.path() + "' requires dataType, valueRank and access");
            }
            nodes.add(new SchemaNode(
                    d.nodeId(), d.parentId(), d.path(), d.name(),
                    kind, dataType, valueRank, access, d.unit(), d.description()));
        }
        return nodes;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String value, String field) {
        try {
            return Enum.valueOf(type, value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("invalid " + field + ": " + value);
        }
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

    public record NodeDto(
            String nodeId, String parentId, String path, String name, String kind,
            String dataType, String valueRank, String access, String unit, String description) {}

    public record CreateDataSourceRequest(
            String name, String protocol, String basis, Integer simulatorPort,
            String realDeviceEndpoint, String runtimeConfig, String securityConfig,
            ConnectionConfigRequest connectionConfig, List<NodeDto> initialSchema) {}

    public record UpdateDataSourceRequest(
            String name, Integer simulatorPort, String realDeviceEndpoint, String runtimeConfig,
            String securityConfig, Boolean enabled, ConnectionConfigRequest connectionConfig) {}

    public record ApplyRescanRequest(List<ScanController.TypeResolutionRequest> typeResolutions) {}

    public record DataSourceResponse(
            String id, String projectId, String name, String protocol, String basis,
            String schemaId, Integer schemaVersion, int simulatorPort, String realDeviceEndpoint,
            String runtimeConfig, String securityConfig, boolean enabled, String runtimeState,
            String credentialState, String serveUrl, Instant createdAt, Instant updatedAt,
            String createdBy, long version, int parameterCount) {

        public static DataSourceResponse from(DataSource d, int parameterCount) {
            return new DataSourceResponse(
                    d.id(), d.projectId(), d.name(), d.protocol().name(), d.basis().name(),
                    d.schemaId(), d.schemaVersion(), d.simulatorPort(), d.realDeviceEndpoint(),
                    d.runtimeConfig(), SecurityConfigRedactor.redact(d.securityConfig()),
                    d.enabled(), d.runtimeState().name(), d.credentialState().name(),
                    d.serveUrl(), d.createdAt(), d.updatedAt(), d.createdBy(), d.version(),
                    parameterCount);
        }
    }
}
