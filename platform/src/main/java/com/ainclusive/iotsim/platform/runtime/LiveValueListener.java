package com.ainclusive.iotsim.platform.runtime;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;

/**
 * Sink for the live (conflated) values a running data source is currently serving
 * (IS-051). The supervisor calls it from {@code applyValues} as values are pushed to
 * the worker; the api layer supplies an implementation that conflates and fans them
 * out over SSE.
 *
 * <p>Called on the supervisor/replay thread, so implementations must be cheap and
 * non-blocking — update a store only. {@link #NONE} is the default when nothing
 * observes values.
 */
@FunctionalInterface
public interface LiveValueListener {

    /** Discards every batch; the default when nothing observes live values. */
    LiveValueListener NONE = (dataSourceId, values, at) -> {};

    void onValues(String dataSourceId, List<NeutralValue> values, Instant at);
}
