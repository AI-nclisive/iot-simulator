package com.ainclusive.iotsim.domain.scan;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.datasource.Protocol;
import com.ainclusive.iotsim.domain.schema.SchemaService;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.platform.Ids;
import com.ainclusive.iotsim.platform.scan.ConnectionTestResult;
import com.ainclusive.iotsim.platform.scan.DiscoveredNode;
import com.ainclusive.iotsim.platform.scan.ScanResult;
import com.ainclusive.iotsim.platform.scan.ScanSpec;
import com.ainclusive.iotsim.platform.scan.SourceScanner;
import com.ainclusive.iotsim.platform.secret.ConnectionCredentials;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Create-from-scan: real-source discovery + data-source creation (backend-specs/05
 * §Scan, SPEC "Create Data Source From Real Source Scan", IS-043).
 *
 * <p>A scan is an async job: {@link #startScan} returns a {@code jobId} immediately
 * and the browse runs on a worker pool; {@link #getScan} polls progress/result.
 * Connection secrets are passed straight to the {@link SourceScanner} for the
 * scan's duration and are never stored in a job, result, or row (backend-specs/08).
 */
@Service
public class ScanService implements DisposableBean {

    // Completed/failed jobs are evicted after this long so the in-memory registry
    // cannot grow without bound in a long-running service. Running jobs are kept.
    private static final Duration JOB_TTL = Duration.ofMinutes(30);

    private final SourceScanner scanner;
    private final ProjectRepository projects;
    private final DataSourceService dataSources;
    private final SchemaService schemas;
    private final Map<String, ScanJob> jobs = new ConcurrentHashMap<>();
    private final Executor executor;
    private final ExecutorService ownedPool;

    @Autowired
    public ScanService(SourceScanner scanner, ProjectRepository projects,
            DataSourceService dataSources, SchemaService schemas) {
        this.scanner = scanner;
        this.projects = projects;
        this.dataSources = dataSources;
        this.schemas = schemas;
        AtomicInteger seq = new AtomicInteger();
        this.ownedPool = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "scan-worker-" + seq.incrementAndGet());
            t.setDaemon(true);
            return t;
        });
        this.executor = ownedPool;
    }

    /** Test seam: run scans on a caller-supplied executor (e.g. synchronous). */
    ScanService(SourceScanner scanner, ProjectRepository projects, DataSourceService dataSources,
            SchemaService schemas, Executor executor) {
        this.scanner = scanner;
        this.projects = projects;
        this.dataSources = dataSources;
        this.schemas = schemas;
        this.executor = executor;
        this.ownedPool = null;
    }

    /** Synchronous reachability/auth probe; secrets are used in memory only. */
    public ConnectionTestResult testConnection(String projectId, String protocol,
            String endpointUrl, ConnectionCredentials credentials) {
        requireProject(projectId);
        validateProtocol(protocol);
        requireEndpoint(endpointUrl);
        return scanner.testConnection(new ScanSpec(protocol, endpointUrl, credentials, 0));
    }

    /** Starts an async scan and returns the {@code RUNNING} job immediately. */
    public ScanJob startScan(String projectId, String protocol, String endpointUrl,
            ConnectionCredentials credentials, int maxNodes) {
        requireProject(projectId);
        validateProtocol(protocol);
        requireEndpoint(endpointUrl);
        evictExpired();
        String jobId = Ids.newId();
        ScanJob job = ScanJob.running(jobId, projectId, protocol, endpointUrl);
        jobs.put(jobId, job);
        ScanSpec spec = new ScanSpec(protocol, endpointUrl, credentials, maxNodes);
        executor.execute(() -> runScan(jobId, spec));
        return job;
    }

    private void runScan(String jobId, ScanSpec spec) {
        try {
            ScanResult result = scanner.scan(spec);
            jobs.computeIfPresent(jobId, (id, job) -> job.completed(result));
        } catch (RuntimeException e) {
            String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            jobs.computeIfPresent(jobId, (id, job) -> job.failed(detail));
        }
    }

    public ScanJob getScan(String projectId, String jobId) {
        ScanJob job = jobs.get(jobId);
        if (job == null || !job.projectId().equals(projectId)) {
            throw new ResourceNotFoundException("ScanJob", jobId);
        }
        evictExpired();
        return job;
    }

    /** Drops completed/failed jobs past their TTL so the registry stays bounded. */
    private void evictExpired() {
        Instant cutoff = Instant.now().minus(JOB_TTL);
        jobs.values().removeIf(job -> !job.isRunning() && job.updatedAt().isBefore(cutoff));
    }

    /**
     * Creates a data source ({@code basis=SCAN}) from a completed scan, persisting
     * the discovered structure as its schema. Folders and known-typed variables are
     * kept; unknown-typed variables are dropped (their resolution UX is IS-044).
     */
    public DataSource createFromScan(String projectId, String jobId, String name,
            String endpoint, String actor) {
        ScanJob job = getScan(projectId, jobId);
        if (job.isRunning()) {
            throw new IllegalArgumentException("scan job is still running: " + jobId);
        }
        if (job.result() == null || job.result().nodes().isEmpty()) {
            throw new IllegalArgumentException("scan job has no discovered structure to create from");
        }
        List<SchemaNode> nodes = toSchemaNodes(job.result().nodes());
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("scan discovered no nodes with a known data type");
        }
        DataSource created = dataSources.create(
                projectId, name, job.protocol(), "SCAN", endpoint, null, null, actor);
        schemas.save(projectId, created.id(), nodes);
        // Re-read so the response carries the linked schemaId/schemaVersion.
        return dataSources.get(projectId, created.id());
    }

    /** Maps discovered nodes to neutral schema nodes, skipping unknown-typed variables. */
    private static List<SchemaNode> toSchemaNodes(List<DiscoveredNode> discovered) {
        List<SchemaNode> nodes = new ArrayList<>();
        for (DiscoveredNode n : discovered) {
            boolean variable = "VARIABLE".equals(n.kind());
            if (variable && n.isUnknownType()) {
                continue; // unknown type cannot become a persisted VARIABLE (IS-044 resolves these)
            }
            nodes.add(new SchemaNode(
                    n.nodeId(),
                    n.parentId(),
                    n.path(),
                    n.name(),
                    variable ? NodeKind.VARIABLE : NodeKind.FOLDER,
                    variable ? DataType.valueOf(n.dataType()) : null,
                    variable ? valueRank(n.valueRank()) : null,
                    variable ? access(n.access()) : null,
                    n.unit(),
                    n.description()));
        }
        return nodes;
    }

    private static ValueRank valueRank(String raw) {
        return raw == null ? ValueRank.SCALAR : ValueRank.valueOf(raw);
    }

    private static Access access(String raw) {
        return raw == null ? Access.READ : Access.valueOf(raw);
    }

    private void requireProject(String projectId) {
        if (projects.findById(projectId).isEmpty()) {
            throw new ResourceNotFoundException("Project", projectId);
        }
    }

    private static void validateProtocol(String protocol) {
        if (protocol == null) {
            throw new IllegalArgumentException("protocol is required");
        }
        Protocol.valueOf(protocol); // invalid -> IllegalArgumentException -> 400
    }

    private static void requireEndpoint(String endpointUrl) {
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new IllegalArgumentException("endpointUrl is required");
        }
    }

    @Override
    public void destroy() {
        if (ownedPool != null) {
            ownedPool.shutdownNow();
        }
    }
}
