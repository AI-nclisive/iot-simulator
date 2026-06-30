package com.ainclusive.iotsim.platform.runtime;

import java.time.Instant;

/**
 * The most recent error observed for a data source: where it came from
 * ({@link HealthOrigin}), a human-readable {@code reason}, and the {@code at}
 * instant it was observed.
 */
public record SourceError(HealthOrigin origin, String reason, Instant at) {}
