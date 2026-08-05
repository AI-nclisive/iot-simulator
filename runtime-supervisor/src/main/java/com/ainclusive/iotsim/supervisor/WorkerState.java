package com.ainclusive.iotsim.supervisor;

/**
 * Worker process lifecycle states owned by the supervisor.
 * See {@code openspec/specs/worker-contract/spec.md} §4.
 */
public enum WorkerState {
    SPAWNED,
    READY,
    CONFIGURED,
    RUNNING,
    STOPPED,
    EXITED
}
