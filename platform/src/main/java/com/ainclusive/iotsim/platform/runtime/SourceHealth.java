package com.ainclusive.iotsim.platform.runtime;

/**
 * Point-in-time health of a data source: its current runtime {@code state}
 * (RUNNING/STARTING/STALE/ERROR/STOPPED, as {@link RuntimeController#state}) plus
 * the most recent {@link SourceError}. {@code lastError} is retained even after
 * recovery (so callers can show "last error was …") and is {@code null} when no
 * error has occurred.
 */
public record SourceHealth(String state, SourceError lastError) {}
