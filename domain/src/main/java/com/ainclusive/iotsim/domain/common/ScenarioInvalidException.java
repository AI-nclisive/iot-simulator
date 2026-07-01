package com.ainclusive.iotsim.domain.common;

import java.util.List;

/** A scenario failed validation and cannot be run (→ HTTP 422). Carries the issue messages. */
public class ScenarioInvalidException extends RuntimeException {
    private final transient List<String> issues;

    public ScenarioInvalidException(String scenarioId, List<String> issues) {
        super("scenario is INVALID: " + scenarioId);
        this.issues = List.copyOf(issues);
    }

    public List<String> issues() {
        return issues;
    }
}
