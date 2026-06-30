package com.ainclusive.iotsim.platform.runtime;

/**
 * Where a data-source health problem originates, as far as the supervisor can
 * reliably tell. {@code SIMULATOR} = the supervisor's own monitoring detected it
 * (unresponsive worker, process exit, restart budget exhausted); {@code PROTOCOL}
 * = the worker reported it over the runtime-events stream; {@code UNKNOWN} =
 * fallback. Finer attribution (source configuration vs Edge Device) is carried in
 * the human-readable reason, not as a distinct value.
 *
 * <p>See SPEC.md → Observe Data Source Health And Errors.
 */
public enum HealthOrigin {
    SIMULATOR,
    PROTOCOL,
    UNKNOWN
}
