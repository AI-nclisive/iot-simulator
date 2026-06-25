package com.ainclusive.iotsim.supervisor;

import java.util.concurrent.CompletionStage;

/** Handle to a launched worker; {@link #close()} terminates it. */
public interface LaunchedWorker extends AutoCloseable {

    @Override
    void close();

    /**
     * Completes when the worker process exits, for any reason — a crash or our own
     * {@link #close()}. The supervisor uses this to detect <em>unexpected</em> exits
     * and trigger restart-with-backoff; an exit that follows an intentional
     * {@code Stop} is not restarted. Never completes exceptionally.
     *
     * <p>See backend-specs/02_WORKER_CONTRACT_AND_IPC.md §4.
     */
    CompletionStage<Void> onExit();
}
