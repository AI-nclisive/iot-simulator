package com.ainclusive.iotsim.supervisor;

import java.time.Duration;

/**
 * Configuration for the per-worker health-monitoring loop (IS-041).
 *
 * <p>The supervisor polls each RUNNING worker via the {@code Health} RPC on
 * {@code pollInterval}. A worker is marked {@code STALE} after
 * {@code staleThreshold} consecutive missed polls, and reverts to
 * {@code RUNNING} as soon as a poll succeeds again.
 *
 * <p>See {@code backend-specs/02_WORKER_CONTRACT_AND_IPC.md} §4.
 */
public record HealthMonitorPolicy(
        Duration pollInterval,
        int staleThreshold) {

    /** Default: poll every 5 s; stale after 3 consecutive misses (15 s window). */
    public static final HealthMonitorPolicy DEFAULT =
            new HealthMonitorPolicy(Duration.ofSeconds(5), 3);

    public HealthMonitorPolicy {
        if (pollInterval.isNegative() || pollInterval.isZero()) {
            throw new IllegalArgumentException("pollInterval must be positive");
        }
        if (staleThreshold < 1) {
            throw new IllegalArgumentException("staleThreshold must be >= 1");
        }
    }
}
