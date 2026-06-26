package com.ainclusive.iotsim.supervisor;

import java.time.Duration;

/**
 * Health-monitoring policy for running workers. The supervisor probes each
 * {@code RUNNING} worker's {@code Health} RPC every {@link #pollInterval()};
 * each probe is bounded by {@link #probeTimeout()} so a hung worker cannot block
 * the monitor. A worker that fails {@link #staleThreshold()} consecutive probes
 * is reported as {@code STALE}; a single good probe clears it back to
 * {@code RUNNING}.
 *
 * <p>This is propagation only — staleness reflects an alive-but-unresponsive
 * worker to the API/UI. An actual process exit is handled separately by
 * restart-with-backoff ({@link RestartPolicy}).
 *
 * <p>See backend-specs/02_WORKER_CONTRACT_AND_IPC.md §4.
 */
public record HealthPolicy(Duration pollInterval, Duration probeTimeout, int staleThreshold) {

    /** Sensible default: probe every 5 s with a 2 s deadline; STALE after 3 consecutive misses. */
    public static final HealthPolicy DEFAULT =
            new HealthPolicy(Duration.ofSeconds(5), Duration.ofSeconds(2), 3);

    public HealthPolicy {
        if (pollInterval == null || pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("pollInterval must be positive");
        }
        if (probeTimeout == null || probeTimeout.isNegative() || probeTimeout.isZero()) {
            throw new IllegalArgumentException("probeTimeout must be positive");
        }
        if (staleThreshold < 1) {
            throw new IllegalArgumentException("staleThreshold must be >= 1");
        }
    }
}
