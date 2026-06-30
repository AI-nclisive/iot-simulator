package com.ainclusive.iotsim.domain.scenario;

/** One step in a scenario flow (backend-specs/03, 06). On write, {@code ordinal} is ignored
 *  and reassigned by list position. */
public record ScenarioStep(int ordinal, String type, String targetSourceId, String params) {}
