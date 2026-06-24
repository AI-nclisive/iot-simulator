package com.ainclusive.iotsim.supervisor;

/**
 * Worker process lifecycle states owned by the supervisor.
 * See {@code backend-specs/02_WORKER_CONTRACT_AND_IPC.md} §4.
 */
public enum WorkerState {
    SPAWNED,
    READY,
    CONFIGURED,
    RUNNING,
    STOPPED,
    EXITED
}
