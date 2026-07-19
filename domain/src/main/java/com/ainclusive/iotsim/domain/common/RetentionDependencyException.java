package com.ainclusive.iotsim.domain.common;

import java.util.List;

/** An artifact delete was rejected because it is still referenced elsewhere (→ HTTP 422). Carries the issue messages. */
public class RetentionDependencyException extends RuntimeException {
    private final transient List<String> issues;

    public RetentionDependencyException(String artifactId, List<String> issues) {
        super("delete of " + artifactId + " is blocked by existing dependents");
        this.issues = List.copyOf(issues);
    }

    public List<String> issues() {
        return issues;
    }
}
