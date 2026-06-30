package com.ainclusive.iotsim.supervisor;

/**
 * Resource-governance policy: a global cap on the number of concurrent
 * long-running source workers the supervisor will run at once. The supervisor
 * refuses a new start once the cap is reached (admission control), per
 * backend-specs/02_WORKER_CONTRACT_AND_IPC.md §5.
 *
 * <p>A non-positive {@code maxConcurrentWorkers} means <em>unlimited</em> — no
 * cap is enforced.
 */
public record ResourceGovernancePolicy(int maxConcurrentWorkers) {

    /** Sensible default: 50 concurrent source workers (each worker is a JVM process). */
    public static final ResourceGovernancePolicy DEFAULT = new ResourceGovernancePolicy(50);

    /** True when a finite cap is enforced (i.e. {@code maxConcurrentWorkers > 0}). */
    public boolean isLimited() {
        return maxConcurrentWorkers > 0;
    }
}
