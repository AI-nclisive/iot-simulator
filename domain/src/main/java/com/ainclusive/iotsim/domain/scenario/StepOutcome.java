package com.ainclusive.iotsim.domain.scenario;

/** Result of executing one scenario step. {@code childRunId}/{@code applied} are null/0 for non-run steps. */
public record StepOutcome(int ordinal, String type, String childRunId, long applied, String state) {}
