package com.ainclusive.iotsim.domain.synthetic;

/**
 * Start-time result of a continuous live synthetic run (Model B, IS-119).
 * The value count is unknown at start (the feed runs until stopped), so it is not
 * part of this summary — read progress via {@code GET /runs/{id}/state} and the
 * final count from the run's evidence once it ends.
 */
public record SyntheticLiveRunSummary(
        String dataSourceId, long seed, String runId, String evidenceId, String state) {}
