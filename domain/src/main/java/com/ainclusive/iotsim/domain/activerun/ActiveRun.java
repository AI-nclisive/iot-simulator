package com.ainclusive.iotsim.domain.activerun;

import java.time.Instant;

/**
 * A lightweight projection of a currently-active {@code runs} row for the dashboard
 * overview panel (IS-122). "Active" means {@code state IN (RUNNING, QUEUED)}.
 *
 * <p>{@code processType} is one of {@code RECORDING}, {@code REPLAY}, or {@code SCENARIO},
 * derived from {@code runs.kind}. {@code runState} mirrors {@code runs.state}.
 * {@code relatedSourceId} and {@code relatedLabel} are the first participating data-source
 * (or the scenario name for SCENARIO runs), both nullable.
 */
public record ActiveRun(
        String id,
        String label,
        String processType,
        String runState,
        Instant startedAt,
        String initiator,
        String relatedSourceId,
        String relatedLabel) {
}
