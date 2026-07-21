package com.ainclusive.iotsim.domain.scan;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.datasource.Protocol;
import com.ainclusive.iotsim.domain.datasource.SourceBasis;
import com.ainclusive.iotsim.domain.schema.SchemaService;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.domain.support.PageCursor;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.platform.Ids;
import com.ainclusive.iotsim.platform.scan.ConnectionTestResult;
import com.ainclusive.iotsim.platform.scan.DiscoveredNode;
import com.ainclusive.iotsim.platform.scan.ScanResult;
import com.ainclusive.iotsim.platform.scan.ScanSpec;
import com.ainclusive.iotsim.platform.scan.SourceScanner;
import com.ainclusive.iotsim.platform.secret.ConnectionCredentials;
import com.ainclusive.iotsim.platform.secret.CredentialStore;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final CredentialStore credentials;
    private final Map<String, ScanJob> jobs = new ConcurrentHashMap<>();
    // Tracks the in-flight scan thread + a cancel-requested flag per job so a
    // running scan can be stopped early (IS-164): cancelScan interrupts the
    // thread, which unblocks WorkerClient.scan's await and cancels the gRPC
    // stream. Both runScan's finish path and cancelScan synchronize on the same
    // ScanExecution instance so a cancel can never land on a pooled thread that
    // has already moved on to a different job (the pool is fixed-size and
    // reused) — the interrupt only happens while the execution is still marked
    // as owning that thread.
    private final Map<String, ScanExecution> executions = new ConcurrentHashMap<>();
    private final Executor executor;

    private static final class ScanExecution {
        private Thread thread;
        private boolean cancelRequested;
        private boolean finished;
    }
    private final ExecutorService ownedPool;

    @Autowired
    public ScanService(SourceScanner scanner, ProjectRepository projects,
            DataSourceService dataSources, SchemaService schemas, CredentialStore credentials) {
        this.scanner = scanner;
        this.projects = projects;
        this.dataSources = dataSources;
        this.schemas = schemas;
        this.credentials = credentials;
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
            SchemaService schemas, CredentialStore credentials, Executor executor) {
        this.scanner = scanner;
        this.projects = projects;
        this.dataSources = dataSources;
        this.schemas = schemas;
        this.credentials = credentials;
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
        executions.put(jobId, new ScanExecution());
        ScanSpec spec = new ScanSpec(protocol, endpointUrl, credentials, maxNodes);
        executor.execute(() -> runScan(jobId, spec));
        return job;
    }

    private void runScan(String jobId, ScanSpec spec) {
        ScanExecution exec = executions.get(jobId);
        synchronized (exec) {
            exec.thread = Thread.currentThread();
            // A cancelScan call that arrived between startScan's executions.put
            // and this point set cancelRequested but found no thread to
            // interrupt (nothing was registered yet) — self-interrupt now so
            // that request isn't silently dropped.
            if (exec.cancelRequested) {
                exec.thread.interrupt();
            }
        }
        try {
            ScanResult result = scanner.scan(spec,
                    (phase, discoveredSoFar) -> jobs.computeIfPresent(
                            jobId, (id, job) -> job.isRunning() ? job.withProgress(phase, discoveredSoFar) : job));
            jobs.computeIfPresent(jobId, (id, job) -> job.completed(result));
        } catch (RuntimeException e) {
            boolean cancelled;
            synchronized (exec) {
                cancelled = exec.cancelRequested;
            }
            if (cancelled) {
                jobs.computeIfPresent(jobId, (id, job) -> job.cancelled());
            } else {
                String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                jobs.computeIfPresent(jobId, (id, job) -> job.failed(detail));
            }
        } finally {
            // Marking finished under the same lock cancelScan checks means a
            // cancel arriving after this point sees `finished` and never calls
            // interrupt() on a thread the pool may have already reassigned.
            synchronized (exec) {
                exec.finished = true;
                exec.thread = null;
            }
            executions.remove(jobId);
            // Clear this thread's interrupt status: interrupt() from cancelScan
            // may have raced past the point WorkerClient.scan consumed it, and a
            // still-interrupted pooled thread would trip the next job it runs.
            Thread.interrupted();
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

    /**
     * Stops a running scan early (IS-164): interrupts the scan thread, which
     * cancels the in-flight gRPC stream to the worker so it stops browsing. A
     * no-op (does not throw) once the job is no longer running — safe to call
     * more than once, or to race against the job's own natural completion.
     */
    public void cancelScan(String projectId, String jobId) {
        getScan(projectId, jobId); // validates the job exists in this project
        ScanExecution exec = executions.get(jobId);
        if (exec == null) {
            return;
        }
        synchronized (exec) {
            if (exec.finished) {
                return;
            }
            exec.cancelRequested = true;
            if (exec.thread != null) {
                exec.thread.interrupt();
            }
        }
    }

    /**
     * Pages a scan job's discovered nodes (IS-165): the job holds the full result
     * in memory (fine — a JVM heap holds far more than a gRPC/JSON message size
     * ceiling), but callers page through it via an offset-encoded cursor instead of
     * ever receiving the whole list in one response. The list is a stable,
     * already-materialized in-memory snapshot (not DB rows), so a simple integer
     * offset is used rather than {@link PageCursor}'s keyset (createdAt/id)
     * encoding — but the limit is clamped via the same {@link PageCursor#clamp}
     * convention every other paged endpoint uses.
     */
    public Page<DiscoveredNode> getScanNodesPage(String projectId, String jobId, String cursor, Integer limit) {
        ScanJob job = getScan(projectId, jobId);
        List<DiscoveredNode> nodes = job.result() == null ? List.of() : job.result().nodes();
        int effectiveLimit = PageCursor.clamp(limit);
        int offset = parseOffset(cursor);
        if (offset >= nodes.size()) {
            return new Page<>(List.of(), null, effectiveLimit);
        }
        int end = Math.min(offset + effectiveLimit, nodes.size());
        String nextCursor = end < nodes.size() ? String.valueOf(end) : null;
        return new Page<>(nodes.subList(offset, end), nextCursor, effectiveLimit);
    }

    private static int parseOffset(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return 0;
        }
        try {
            return Math.max(Integer.parseInt(cursor), 0);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid cursor: " + cursor);
        }
    }

    /** Drops completed/failed jobs past their TTL so the registry stays bounded. */
    private void evictExpired() {
        Instant cutoff = Instant.now().minus(JOB_TTL);
        jobs.values().removeIf(job -> !job.isRunning() && job.updatedAt().isBefore(cutoff));
    }

    /**
     * Creates a data source ({@code basis=SCAN}) from a completed scan, persisting the
     * discovered structure as its schema (IS-044). Folders and known-typed variables
     * are kept as-is; each unknown-typed variable must be addressed by a
     * {@link TypeResolution} — assigned a neutral type (kept) or excluded (dropped).
     * An unknown-typed variable with no resolution rejects the create (400), per
     * backend-specs/01 §2 "unknown types require user resolution before create".
     *
     * @param resolutions user decisions for unknown-typed nodes; may be {@code null}/empty
     */
    public DataSource createFromScan(String projectId, String jobId, String name, String endpoint,
            List<TypeResolution> resolutions, String actor) {
        ScanJob job = getScan(projectId, jobId);
        if (job.isRunning()) {
            throw new IllegalArgumentException("scan job is still running: " + jobId);
        }
        if (job.result() == null || job.result().nodes().isEmpty()) {
            throw new IllegalArgumentException("scan job has no discovered structure to create from");
        }
        List<SchemaNode> nodes = populateSchema(job.result().nodes(), resolutions);
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("scan produced an empty schema after resolution");
        }
        DataSource created = dataSources.create(
                projectId, name, job.protocol(), "SCAN", null, endpoint, null, null, null, null, actor);
        schemas.save(projectId, created.id(), nodes);
        // Re-read so the response carries the linked schemaId/schemaVersion.
        return dataSources.get(projectId, created.id());
    }

    /**
     * Starts a re-scan of an already-created (basis=SCAN) data source's real endpoint,
     * reusing its stored protocol, real-device endpoint, and connection credentials —
     * the same reuse RecordingService.startCapture makes to reconnect for a live
     * capture. Poll and apply the result the same way as a create-time scan.
     *
     * @throws IllegalArgumentException if the source isn't SCAN-basis or has no endpoint
     */
    public ScanJob startRescan(String projectId, String dataSourceId) {
        DataSource source = dataSources.get(projectId, dataSourceId);
        if (source.basis() != SourceBasis.SCAN) {
            throw new IllegalArgumentException("only a SCAN-basis data source can be rescanned");
        }
        if (source.realDeviceEndpoint() == null || source.realDeviceEndpoint().isBlank()) {
            throw new IllegalArgumentException("data source has no real-device endpoint to rescan");
        }
        ConnectionCredentials creds = credentials.find(dataSourceId).orElse(null);
        return startScan(projectId, source.protocol().name(), source.realDeviceEndpoint(), creds, 0);
    }

    /**
     * Applies a completed rescan job's discovered structure onto an existing data
     * source, saving it as a new schema version (IS-094 breaking-impact guard applies
     * the same as any other manual schema edit). Unlike {@link #createFromScan}, no
     * new data source is created — the existing id keeps its identity, runtime state,
     * and credentials.
     *
     * @param resolutions user decisions for unknown-typed nodes; may be {@code null}/empty
     */
    public DataSource applyRescan(String projectId, String dataSourceId, String jobId,
            List<TypeResolution> resolutions) {
        ScanJob job = getScan(projectId, jobId);
        if (job.isRunning()) {
            throw new IllegalArgumentException("scan job is still running: " + jobId);
        }
        if (job.result() == null || job.result().nodes().isEmpty()) {
            throw new IllegalArgumentException("scan job has no discovered structure to apply");
        }
        List<SchemaNode> nodes = populateSchema(job.result().nodes(), resolutions);
        if (nodes.isEmpty()) {
            throw new IllegalArgumentException("scan produced an empty schema after resolution");
        }
        schemas.save(projectId, dataSourceId, nodes);
        return dataSources.get(projectId, dataSourceId);
    }

    /**
     * Maps discovered nodes to neutral schema nodes, applying per-node type
     * resolutions to unknown-typed variables. Folders and known-typed variables pass
     * through. Throws if a resolution targets a node that is not an unknown-typed
     * variable, or if any unknown-typed variable is left unresolved.
     */
    private static List<SchemaNode> populateSchema(
            List<DiscoveredNode> discovered, List<TypeResolution> resolutions) {
        Map<String, TypeResolution> byNodeId = indexResolutions(discovered, resolutions);
        List<SchemaNode> nodes = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        for (DiscoveredNode n : discovered) {
            if ("VARIABLE".equals(n.kind()) && n.isUnknownType()) {
                TypeResolution r = byNodeId.get(n.nodeId());
                if (r == null) {
                    unresolved.add(n.path() == null ? n.nodeId() : n.path());
                } else if (!r.exclude()) {
                    nodes.add(variableNode(n, DataType.valueOf(r.dataType()),
                            r.valueRank() == null ? valueRank(n.valueRank()) : ValueRank.valueOf(r.valueRank()),
                            r.access() == null ? access(n.access()) : Access.valueOf(r.access())));
                }
            } else if ("VARIABLE".equals(n.kind())) {
                nodes.add(variableNode(n, DataType.valueOf(n.dataType()),
                        valueRank(n.valueRank()), access(n.access())));
            } else {
                nodes.add(new SchemaNode(n.nodeId(), n.parentId(), n.path(), n.name(),
                        NodeKind.FOLDER, null, null, null, n.unit(), n.description()));
            }
        }
        if (!unresolved.isEmpty()) {
            throw new IllegalArgumentException(
                    "scan has unknown-typed nodes requiring resolution before create: " + unresolved);
        }
        return nodes;
    }

    /** Indexes resolutions by nodeId, rejecting duplicates and non-unknown targets. */
    private static Map<String, TypeResolution> indexResolutions(
            List<DiscoveredNode> discovered, List<TypeResolution> resolutions) {
        if (resolutions == null || resolutions.isEmpty()) {
            return Map.of();
        }
        Set<String> unknownIds = new HashSet<>();
        for (DiscoveredNode n : discovered) {
            if (n.isUnknownType()) {
                unknownIds.add(n.nodeId());
            }
        }
        Map<String, TypeResolution> byNodeId = new HashMap<>();
        for (TypeResolution r : resolutions) {
            if (!unknownIds.contains(r.nodeId())) {
                throw new IllegalArgumentException(
                        "resolution target is not an unknown-typed node: " + r.nodeId());
            }
            if (byNodeId.put(r.nodeId(), r) != null) {
                throw new IllegalArgumentException("duplicate resolution for node: " + r.nodeId());
            }
        }
        return byNodeId;
    }

    private static SchemaNode variableNode(
            DiscoveredNode n, DataType dataType, ValueRank valueRank, Access access) {
        return new SchemaNode(n.nodeId(), n.parentId(), n.path(), n.name(),
                NodeKind.VARIABLE, dataType, valueRank, access, n.unit(), n.description());
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
