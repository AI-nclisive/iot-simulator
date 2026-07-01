package com.ainclusive.iotsim.domain.scenario;

import java.util.List;

/** Outcome of a scenario run: the parent SCENARIO run + per-step results. */
public record ScenarioRunSummary(String runId, String evidenceId, String status, List<StepOutcome> steps) {}
