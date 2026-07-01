package com.ainclusive.iotsim.domain.scenario;

/** One validation finding. {@code ordinal} = -1 for scenario-level issues. */
public record ValidationIssue(int ordinal, String severity, String message) {
    public static final String ERROR = "ERROR";
    public static final String WARNING = "WARNING";
}
