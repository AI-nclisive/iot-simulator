package com.ainclusive.iotsim.supervisor;

/** Handle to a launched worker; {@link #close()} terminates it. */
@FunctionalInterface
public interface LaunchedWorker extends AutoCloseable {

    @Override
    void close();
}
