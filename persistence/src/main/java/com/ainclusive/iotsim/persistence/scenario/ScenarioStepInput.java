package com.ainclusive.iotsim.persistence.scenario;

/** Write-side step; {@code ordinal} is assigned by list position at persist time. */
public record ScenarioStepInput(String type, String targetSourceId, String params) {}
