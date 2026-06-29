package com.ainclusive.iotsim.api.stream;

import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conflated live-value state: the latest value per (dataSourceId, nodeId), plus the
 * set of nodes changed since the last flush. Lock-free; a benign race may re-emit a
 * value on the next flush (idempotent for a latest-value view).
 */
final class LiveValueStore {

    private final Map<String, Map<String, NeutralValue>> latest = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> dirty = new ConcurrentHashMap<>();

    void record(String dataSourceId, List<NeutralValue> values) {
        Map<String, NeutralValue> bySource =
                latest.computeIfAbsent(dataSourceId, k -> new ConcurrentHashMap<>());
        Set<String> dirtyNodes =
                dirty.computeIfAbsent(dataSourceId, k -> ConcurrentHashMap.newKeySet());
        for (NeutralValue v : values) {
            bySource.put(v.nodeId(), v);
            dirtyNodes.add(v.nodeId());
        }
    }

    List<NeutralValue> snapshot(String dataSourceId) {
        Map<String, NeutralValue> bySource = latest.get(dataSourceId);
        return bySource == null ? List.of() : new ArrayList<>(bySource.values());
    }

    List<NeutralValue> drainChanged(String dataSourceId) {
        Set<String> dirtyNodes = dirty.get(dataSourceId);
        Map<String, NeutralValue> bySource = latest.get(dataSourceId);
        if (dirtyNodes == null || bySource == null || dirtyNodes.isEmpty()) {
            return List.of();
        }
        List<NeutralValue> changed = new ArrayList<>();
        for (String nodeId : List.copyOf(dirtyNodes)) {
            dirtyNodes.remove(nodeId);
            NeutralValue v = bySource.get(nodeId);
            if (v != null) {
                changed.add(v);
            }
        }
        return changed;
    }

    Set<String> dirtySources() {
        Set<String> result = ConcurrentHashMap.newKeySet();
        dirty.forEach((source, nodes) -> {
            if (!nodes.isEmpty()) {
                result.add(source);
            }
        });
        return result;
    }
}
