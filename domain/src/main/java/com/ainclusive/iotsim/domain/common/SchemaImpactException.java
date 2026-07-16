package com.ainclusive.iotsim.domain.common;

import java.util.List;

/** A schema save would break an existing dependent and was rejected (→ HTTP 422). Carries the issue messages. */
public class SchemaImpactException extends RuntimeException {
    private final transient List<String> issues;

    public SchemaImpactException(String dataSourceId, List<String> issues) {
        super("schema save for " + dataSourceId + " breaks existing dependents");
        this.issues = List.copyOf(issues);
    }

    public List<String> issues() {
        return issues;
    }
}
