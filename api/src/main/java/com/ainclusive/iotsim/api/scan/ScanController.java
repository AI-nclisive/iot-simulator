package com.ainclusive.iotsim.api.scan;

import com.ainclusive.iotsim.api.datasource.DataSourceController.DataSourceResponse;
import com.ainclusive.iotsim.api.support.ConnectionConfigRequest;
import com.ainclusive.iotsim.api.support.CredentialRequests;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.scan.ScanJob;
import com.ainclusive.iotsim.domain.scan.ScanService;
import com.ainclusive.iotsim.platform.scan.ConnectionTestResult;
import com.ainclusive.iotsim.platform.scan.DiscoveredNode;
import com.ainclusive.iotsim.platform.scan.ScanResult;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
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
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/data-sources/scan")
public class ScanController {

    private final ScanService scans;

    public ScanController(ScanService scans) {
        this.scans = scans;
    }

    /** Reachability/auth probe (synchronous). */
    @PostMapping("/test-connection")
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
    @PostMapping
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
    @GetMapping("/{jobId}")
    public ScanJobResponse get(@PathVariable String projectId, @PathVariable String jobId) {
        return ScanJobResponse.from(scans.getScan(projectId, jobId));
    }

    /** Creates a data source (basis=SCAN) from a completed scan. */
    @PostMapping("/{jobId}/create")
    public ResponseEntity<DataSourceResponse> create(
            @PathVariable String projectId, @PathVariable String jobId,
            @RequestBody CreateFromScanRequest req) {
        require(req != null && notBlank(req.name()), "name is required");
        DataSource ds = scans.createFromScan(projectId, jobId, req.name(), req.endpoint(), "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/data-sources/" + ds.id()))
                .eTag("\"" + ds.version() + "\"")
                .body(DataSourceResponse.from(ds));
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

    public record CreateFromScanRequest(String name, String endpoint) {}

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
            String dataType, String valueRank, String access, String unit, String description) {

        static DiscoveredNodeResponse from(DiscoveredNode n) {
            return new DiscoveredNodeResponse(n.nodeId(), n.parentId(), n.path(), n.name(), n.kind(),
                    n.dataType(), n.valueRank(), n.access(), n.unit(), n.description());
        }
    }
}
