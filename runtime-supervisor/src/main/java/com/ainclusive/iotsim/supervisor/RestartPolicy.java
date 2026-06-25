package com.ainclusive.iotsim.supervisor;

import java.time.Duration;

/**
 * Exponential restart-with-backoff policy for unexpected worker failures.
 *
 * <p>The supervisor restarts a worker that exits unexpectedly, waiting
 * {@code initialBackoff * multiplier^(attempt-1)} (1-based attempt) before each
 * try, capped per-attempt at {@code maxBackoff}, and gives up after
 * {@code maxRestarts} attempts. Intentional stops are never restarted, so the cap
 * only bounds recovery from genuine failures.
 *
 * <p>See backend-specs/02_WORKER_CONTRACT_AND_IPC.md §4.
 */
public record RestartPolicy(
        Duration initialBackoff, double multiplier, Duration maxBackoff, int maxRestarts) {

    /** Sensible default: 500ms → 1s → 2s … capped at 30s, up to 5 attempts. */
    public static final RestartPolicy DEFAULT =
            new RestartPolicy(Duration.ofMillis(500), 2.0, Duration.ofSeconds(30), 5);

    public RestartPolicy {
        if (initialBackoff == null || initialBackoff.isNegative()) {
            throw new IllegalArgumentException("initialBackoff must be non-negative");
        }
        if (multiplier < 1.0) {
            throw new IllegalArgumentException("multiplier must be >= 1.0");
        }
        if (maxBackoff == null || maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff must be >= initialBackoff");
        }
        if (maxRestarts < 0) {
            throw new IllegalArgumentException("maxRestarts must be >= 0");
        }
    }

    /**
     * Backoff delay before the given 1-based restart {@code attempt}, capped at
     * {@link #maxBackoff()}.
     */
    public Duration backoffFor(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1");
        }
        double scaled = initialBackoff.toMillis() * Math.pow(multiplier, attempt - 1);
        long millis = (long) Math.min(scaled, (double) maxBackoff.toMillis());
        return Duration.ofMillis(Math.max(millis, 0));
    }
}
