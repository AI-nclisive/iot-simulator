package com.ainclusive.iotsim.domain.run;

/** Per-source runtime state within a run (IS-089). */
public record SourceState(String sourceId, String state, String lastError) {
}
