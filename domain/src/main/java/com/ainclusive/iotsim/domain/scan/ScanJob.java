package com.ainclusive.iotsim.domain.scan;

import com.ainclusive.iotsim.platform.scan.ScanPhase;
import com.ainclusive.iotsim.platform.scan.ScanResult;
import java.time.Instant;

/**
 * An async create-from-scan job. Ephemeral (held in memory only, never persisted —
 * scans are modelled as polled async jobs per backend-specs/05_API_CONTRACT.md).
 * Immutable: the worker thread replaces the registry entry on completion, and on
 * every in-flight progress tick (IS-163) so a poller always reads a consistent
 * snapshot.
 *
 * <p>{@code state} is {@code RUNNING} while in flight, then the discovery
 * {@code ScanStatus} (OK / PARTIAL / UNREACHABLE / AUTH_FAILURE / UNSUPPORTED), or
 * {@code FAILED} on an unexpected error. Never carries credentials/secrets.
 * {@code phase}/{@code discoveredSoFar} are only meaningful while {@code RUNNING};
 * terminal jobs carry the full result instead.
 */
public record ScanJob(
        String jobId,
        String projectId,
        String protocol,
        String endpointUrl,
        String state,
        ScanPhase phase,
        int discoveredSoFar,
        ScanResult result,
        String message,
        Instant createdAt,
        Instant updatedAt) {

    static final String RUNNING = "RUNNING";
    static final String FAILED = "FAILED";

    static ScanJob running(String jobId, String projectId, String protocol, String endpointUrl) {
        Instant now = Instant.now();
        return new ScanJob(jobId, projectId, protocol, endpointUrl, RUNNING,
                ScanPhase.CONNECTING, 0, null, "scan in progress", now, now);
    }

    ScanJob withProgress(ScanPhase phase, int discoveredSoFar) {
        return new ScanJob(jobId, projectId, protocol, endpointUrl, state,
                phase, discoveredSoFar, result, message, createdAt, Instant.now());
    }

    ScanJob completed(ScanResult result) {
        return new ScanJob(jobId, projectId, protocol, endpointUrl, result.status().name(),
                null, 0, result, result.message(), createdAt, Instant.now());
    }

    ScanJob failed(String message) {
        return new ScanJob(jobId, projectId, protocol, endpointUrl, FAILED,
                null, 0, null, message, createdAt, Instant.now());
    }

    public boolean isRunning() {
        return RUNNING.equals(state);
    }
}
