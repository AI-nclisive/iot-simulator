package com.ainclusive.iotsim.platform.scan;

/**
 * Port for real-source discovery (create-from-scan). The domain depends on this
 * abstraction; the runtime supervisor provides the real implementation (which
 * drives a protocol worker in client mode), and an "unsupported" stub is used when
 * no workers are launched. Kept protocol-neutral and free of domain types so the
 * domain need not depend on the supervisor.
 *
 * <p>See backend-specs/02_WORKER_CONTRACT_AND_IPC.md §6 and 05_API_CONTRACT.md §Scan.
 */
public interface SourceScanner {

    /** Probes reachability and authentication without browsing the address space. */
    ConnectionTestResult testConnection(ScanSpec spec);

    /** Connects and browses the real source into a discovered neutral structure. */
    default ScanResult scan(ScanSpec spec) {
        return scan(spec, (phase, discoveredSoFar) -> { });
    }

    /** Same as {@link #scan(ScanSpec)}, reporting in-flight progress via {@code onProgress}. */
    ScanResult scan(ScanSpec spec, ScanProgressListener onProgress);
}
