package com.ainclusive.iotsim.domain.evidence;

/**
 * How complete an evidence artifact is (openspec/specs/artifact-formats/spec.md). Mirrors the run outcome:
 * {@code COMPLETE} for a run that finished cleanly, {@code FAILED} for one that
 * failed, {@code STOPPED} when an operator deliberately ends an open-ended run,
 * and {@code PARTIAL} when data is genuinely missing.
 */
public enum Completeness {
    COMPLETE,
    STOPPED,
    PARTIAL,
    FAILED
}
