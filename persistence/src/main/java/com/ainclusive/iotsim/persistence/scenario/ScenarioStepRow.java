package com.ainclusive.iotsim.persistence.scenario;

/** Read-side projection of a {@code scenario_steps} row. */
public record ScenarioStepRow(int ordinal, String type, String targetSourceId, String params) {}
