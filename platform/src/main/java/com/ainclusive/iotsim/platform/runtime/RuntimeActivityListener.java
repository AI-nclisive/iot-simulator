package com.ainclusive.iotsim.platform.runtime;

/**
 * Sink for {@link RuntimeActivityEvent}s the supervisor receives from running
 * workers (IS-048). The app supplies an implementation to persist them as
 * runtime-event history (IS-049/IS-055); the supervisor calls it as events arrive
 * on each worker's {@code RuntimeEvents} stream.
 *
 * <p>Called on an IPC delivery thread, so implementations must be cheap and
 * non-blocking — hand off to a queue/executor for any real work. {@link #NONE} is
 * the default when no observer is wired.
 */
@FunctionalInterface
public interface RuntimeActivityListener {

    /** Discards every event; the default when nothing observes runtime activity. */
    RuntimeActivityListener NONE = event -> {};

    void onRuntimeActivity(RuntimeActivityEvent event);
}
