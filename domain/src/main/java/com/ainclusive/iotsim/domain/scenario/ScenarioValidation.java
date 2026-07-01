package com.ainclusive.iotsim.domain.scenario;

import java.util.List;

/** Result of validating a scenario: derived status + the findings behind it. */
public record ScenarioValidation(String status, List<ValidationIssue> issues) {
    public static final String READY = "READY";
    public static final String INVALID = "INVALID";
}
