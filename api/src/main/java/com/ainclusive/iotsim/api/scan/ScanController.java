package com.ainclusive.iotsim.api.scan;

import com.ainclusive.iotsim.api.datasource.DataSourceController.DataSourceResponse;
import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.api.support.ConnectionConfigRequest;
import com.ainclusive.iotsim.api.support.CredentialRequests;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.scan.ScanJob;
import com.ainclusive.iotsim.domain.scan.ScanService;
import com.ainclusive.iotsim.domain.scan.TypeResolution;
import com.ainclusive.iotsim.domain.schema.SchemaService;
import com.ainclusive.iotsim.platform.scan.ConnectionTestResult;
import com.ainclusive.iotsim.platform.scan.DiscoveredNode;
import com.ainclusive.iotsim.platform.scan.ScanResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Create-from-scan: test a real source's connection, run an async discovery scan,
 * poll the job, and create a data source from the discovered structure. Mirrors
 * backend-specs/05_API_CONTRACT.md §Scan. Credentials are write-only and never
 * echoed; secrets are session-only (backend-specs/08).
 *
 * <p>Authorization (IS-077): all scan operations are part of creating a data source
 * (admin-level) — {@link Permission#SOURCE_EDIT}. Poll (GET) uses {@link Permission#OBSERVE}.
 */
@RestController
@Tag(name = "Data Sources")
@RequestMapping("/api/v1/projects/{projectId}/data-sources/scan")
public class ScanController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";
    private static final String SOURCE_EDIT =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).SOURCE_EDIT)";

    private final ScanService scans;
    private final SchemaService schemas;

    public ScanController(ScanService scans, SchemaService schemas) {
        this.scans = scans;
        this.schemas = schemas;
    }

    /** Reachability/auth probe (synchronous). */
    @Operation(
            summary = "Test endpoint connectivity",
            description = "Synchronously probes reachability and authentication against a real endpoint"
                    + " without scanning or persisting anything.")
    @PostMapping("/test-connection")
    @PreAuthorize(SOURCE_EDIT)
    public ConnectionTestResponse testConnection(
            @PathVariable String projectId, @RequestBody ScanRequest req) {
        require(req != null, "request body is required");
        require(notBlank(req.endpointUrl()), "endpointUrl is required");
        require(notBlank(req.protocol()), "protocol is required");
        ConnectionTestResult result = scans.testConnection(
                projectId, req.protocol(), req.endpointUrl(),
                CredentialRequests.toCredentials(req.connectionConfig()));
        return new ConnectionTestResponse(result.status().name(), result.message());
    }

    /** Starts an async scan; returns 202 with the job id to poll. */
    @Operation(
            summary = "Start a discovery scan",
            description = "Starts an asynchronous scan of the endpoint's address space and returns 202 Accepted"
                    + " with a Location header and the job id to poll.")
    @PostMapping
    @PreAuthorize(SOURCE_EDIT)
    public ResponseEntity<StartScanResponse> startScan(
            @PathVariable String projectId, @RequestBody ScanRequest req) {
        require(req != null, "request body is required");
        require(notBlank(req.endpointUrl()), "endpointUrl is required");
        require(notBlank(req.protocol()), "protocol is required");
        ScanJob job = scans.startScan(
                projectId, req.protocol(), req.endpointUrl(),
                CredentialRequests.toCredentials(req.connectionConfig()),
                req.maxNodes() == null ? 0 : req.maxNodes());
        URI location = URI.create(
                "/api/v1/projects/" + projectId + "/data-sources/scan/" + job.jobId());
        return ResponseEntity.accepted().location(location)
                .body(new StartScanResponse(job.jobId(), job.state()));
    }

    /** Polls a scan job: progress while running, then results/states. */
    @Operation(
            summary = "Poll a scan job",
            description = "Returns the scan job's status while running, then its discovered nodes and counts"
                    + " once complete.")
    @GetMapping("/{jobId}")
    @PreAuthorize(OBSERVE)
    public ScanJobResponse get(@PathVariable String projectId, @PathVariable String jobId) {
        return ScanJobResponse.from(scans.getScan(projectId, jobId));
    }

    /**
     * Creates a data source (basis=SCAN) from a completed scan. Unknown-typed
     * discovered nodes must be addressed via {@code typeResolutions} (assign a type
     * or exclude); an unresolved unknown node rejects the request (400).
     */
    @Operation(
            summary = "Create data source from scan",
            description = "Materializes a data source from a completed scan's discovered nodes,"
                    + " applying type resolutions for unknown-typed nodes. Returns 201 Created with a"
                    + " Location header.")
    @PostMapping("/{jobId}/create")
    @PreAuthorize(SOURCE_EDIT)
    public ResponseEntity<DataSourceResponse> create(
            @PathVariable String projectId, @PathVariable String jobId,
            @RequestBody CreateFromScanRequest req) {
        require(req != null && notBlank(req.name()), "name is required");
        DataSource ds = scans.createFromScan(projectId, jobId, req.name(), req.realDeviceEndpoint(),
                toResolutions(req.typeResolutions()), "local");
        int paramCount = schemas.countVariableNodes(ds.id());
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/data-sources/" + ds.id()))
                .eTag("\"" + ds.version() + "\"")
                .body(DataSourceResponse.from(ds, paramCount));
    }

    private static List<TypeResolution> toResolutions(List<TypeResolutionRequest> reqs) {
        if (reqs == null) {
            return List.of();
        }
        return reqs.stream()
                .map(r -> new TypeResolution(r.nodeId(), r.dataType(), r.valueRank(), r.access(),
                        r.exclude() != null && r.exclude()))
                .toList();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    public record ScanRequest(
            String protocol, String endpointUrl, Integer maxNodes,
            ConnectionConfigRequest connectionConfig) {}

    public record CreateFromScanRequest(
            String name, String realDeviceEndpoint, List<TypeResolutionRequest> typeResolutions) {}

    /**
     * A user's decision for one unknown-typed discovered node: assign {@code dataType}
     * (optionally {@code valueRank}/{@code access}) to keep it, or {@code exclude=true}
     * to drop it. Targets a node by {@code nodeId}.
     */
    public record TypeResolutionRequest(
            String nodeId, String dataType, String valueRank, String access, Boolean exclude) {}

    public record ConnectionTestResponse(String status, String message) {}

    public record StartScanResponse(String jobId, String status) {}

    public record ScanJobResponse(
            String jobId, String status, boolean truncated, int discoveredCount,
            int unknownCount, String message, List<DiscoveredNodeResponse> nodes) {

        static ScanJobResponse from(ScanJob job) {
            ScanResult result = job.result();
            List<DiscoveredNodeResponse> nodes = result == null ? List.of()
                    : result.nodes().stream().map(DiscoveredNodeResponse::from).toList();
            return new ScanJobResponse(
                    job.jobId(),
                    job.state(),
                    result != null && result.truncated(),
                    nodes.size(),
                    result == null ? 0 : result.unknownCount(),
                    job.message(),
                    nodes);
        }
    }

    public record DiscoveredNodeResponse(
            String nodeId, String parentId, String path, String name, String kind,
            String dataType, String valueRank, String access, String unit, String description,
            boolean unknownType) {

        static DiscoveredNodeResponse from(DiscoveredNode n) {
            return new DiscoveredNodeResponse(n.nodeId(), n.parentId(), n.path(), n.name(), n.kind(),
                    n.dataType(), n.valueRank(), n.access(), n.unit(), n.description(), n.isUnknownType());
        }
    }
}
