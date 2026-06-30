package com.ainclusive.iotsim.domain.evidence;

/**
 * How complete an evidence artifact is (backend-specs/06). Mirrors the run outcome:
 * {@code COMPLETE} for a run that finished cleanly, {@code FAILED} for one that
 * failed, {@code PARTIAL} otherwise (stopped early or sections missing).
 */
public enum Completeness {
    COMPLETE,
    PARTIAL,
    FAILED
}
