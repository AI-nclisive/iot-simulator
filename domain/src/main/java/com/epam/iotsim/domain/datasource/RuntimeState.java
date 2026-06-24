package com.epam.iotsim.domain.datasource;

/**
 * Runtime state of a data-source. Owned by the runtime supervisor, not the
 * relational store (backend-specs/03_DOMAIN_MODEL.md).
 */
public enum RuntimeState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR,
    STALE
}
