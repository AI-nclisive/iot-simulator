package com.ainclusive.iotsim.domain.datasource;

/**
 * Runtime state of a data-source. Owned by the runtime supervisor, not the
 * relational store (openspec/specs/domain-model/spec.md).
 */
public enum RuntimeState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
    STALE
}
