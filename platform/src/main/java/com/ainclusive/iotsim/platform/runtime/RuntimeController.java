package com.ainclusive.iotsim.platform.runtime;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.util.List;
import java.util.Map;

/**
 * Port for controlling a data-source's runtime. The domain depends on this
 * abstraction; the runtime supervisor provides the real implementation, an
 * in-memory one is used when no workers are launched. State is a plain string
 * (e.g. RUNNING, STOPPED) to keep this port free of domain types.
 *
 * <p>See backend-specs/02_WORKER_CONTRACT_AND_IPC.md and 03_DOMAIN_MODEL.md.
 */
public interface RuntimeController {

    /** Starts (or returns the state of an already-running) data-source. */
    String start(String dataSourceId, RuntimeStartSpec spec);

    /** Stops the data-source if running. */
    String stop(String dataSourceId);

    /** Current runtime state; STOPPED when not running. */
    String state(String dataSourceId);

    /**
     * Current health: the runtime {@link #state} plus the most recent error, if
     * any. The default reports {@code state} with no error detail; the runtime
     * supervisor overrides it to surface staleness/exit reasons.
     */
    default SourceHealth health(String dataSourceId) {
        return new SourceHealth(state(dataSourceId), null);
    }

    /** Pushes neutral values to the running data-source (replay/synthetic). Returns the count applied. */
    long applyValues(String dataSourceId, List<NeutralValue> values);

    /**
     * Injects or clears a named fault on the running data-source worker. A default
     * no-op is provided so existing implementations remain valid; the supervisor
     * overrides this to forward the RPC to the worker.
     */
    default void injectFault(String dataSourceId, String kind, String layer, boolean active,
            Map<String, String> params) {
        // no-op by default; implementations override
    }
}
