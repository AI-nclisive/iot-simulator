package com.ainclusive.iotsim.supervisor;

/**
 * Worker process lifecycle states owned by the supervisor.
 * See {@code backend-specs/02_WORKER_CONTRACT_AND_IPC.md} §4.
 *
 * <p>{@link #STALE} is set by the health-monitoring loop (IS-041) when a running
 * worker stops responding to {@code Health} RPCs; it transitions back to
 * {@link #RUNNING} when the next poll succeeds, or to {@link #EXITED} once the
 * worker process itself exits and the restart budget is exhausted.
 */
public enum WorkerState {
    SPAWNED,
    READY,
    CONFIGURED,
    RUNNING,
    /** Worker process is alive but not responding to Health polls. */
    STALE,
    STOPPED,
    EXITED
}
