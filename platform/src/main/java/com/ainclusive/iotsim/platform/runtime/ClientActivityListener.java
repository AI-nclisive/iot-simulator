package com.ainclusive.iotsim.platform.runtime;

/**
 * Sink for {@link ClientActivityEvent}s the supervisor receives from running
 * workers (IS-047). The domain/app supplies an implementation to observe and
 * fan out client activity (connected-client observation, live SSE); the
 * supervisor calls it as events arrive on each worker's {@code ClientEvents}
 * stream.
 *
 * <p>Called on an IPC delivery thread, so implementations must be cheap and
 * non-blocking — hand off to a queue/executor for any real work. {@link #NONE}
 * is the default when no observer is wired.
 */
@FunctionalInterface
public interface ClientActivityListener {

    /** Discards every event; the default when nothing observes client activity. */
    ClientActivityListener NONE = event -> {};

    void onClientActivity(ClientActivityEvent event);
}
