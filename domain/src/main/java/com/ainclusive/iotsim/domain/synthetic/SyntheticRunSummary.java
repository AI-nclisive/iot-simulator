package com.ainclusive.iotsim.domain.synthetic;

/**
 * Result of a Model-A synthetic run: how many generated values were applied to the
 * source, the effective (captured) seed, and the run/evidence ids.
 */
public record SyntheticRunSummary(String dataSourceId, long valueCount, long seed, String runId, String evidenceId) {}
