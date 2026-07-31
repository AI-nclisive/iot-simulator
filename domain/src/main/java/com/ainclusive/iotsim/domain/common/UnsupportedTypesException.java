package com.ainclusive.iotsim.domain.common;

import java.util.List;

/** A schema references native OPC UA types that cannot be captured/replayed (IS-199,
 * → HTTP 422). Carries one diagnostic per affected variable. */
public class UnsupportedTypesException extends RuntimeException {
    private final transient List<String> issues;

    public UnsupportedTypesException(List<String> issues) {
        super("schema declares unsupported native types");
        this.issues = List.copyOf(issues);
    }

    public List<String> issues() {
        return issues;
    }
}
