package com.epam.iotsim.platform.runtime;

import com.epam.iotsim.protocolmodel.NeutralValue;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory runtime controller used when no out-of-process workers are launched
 * (default local/dev mode). Tracks state only; spawns nothing.
 */
public class InMemoryRuntimeController implements RuntimeController {

    private static final String RUNNING = "RUNNING";
    private static final String STOPPED = "STOPPED";

    private final Map<String, String> states = new ConcurrentHashMap<>();
    private final Map<String, Long> appliedCounts = new ConcurrentHashMap<>();

    @Override
    public String start(String dataSourceId, RuntimeStartSpec spec) {
        states.put(dataSourceId, RUNNING);
        return RUNNING;
    }

    @Override
    public String stop(String dataSourceId) {
        states.put(dataSourceId, STOPPED);
        return STOPPED;
    }

    @Override
    public String state(String dataSourceId) {
        return states.getOrDefault(dataSourceId, STOPPED);
    }

    @Override
    public long applyValues(String dataSourceId, List<NeutralValue> values) {
        appliedCounts.merge(dataSourceId, (long) values.size(), Long::sum);
        return values.size();
    }

    /** Total values applied to a source (test/introspection helper). */
    public long appliedCount(String dataSourceId) {
        return appliedCounts.getOrDefault(dataSourceId, 0L);
    }
}
