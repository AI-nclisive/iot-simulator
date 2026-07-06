package com.ainclusive.iotsim.api.stream;

import java.util.Objects;

/** Routing key for a live stream: a stream type scoped to one resource id. */
public record StreamKey(Type type, String scopeId) {

    /** Stream families exposed over SSE; each maps to one endpoint. */
    public enum Type {
        RUNTIME,
        CLIENTS,
        VALUES,
        SCENARIO_RUN
    }

    public StreamKey {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(scopeId, "scopeId");
    }

    /** Runtime-context stream for a project: {@code /projects/{projectId}/stream/runtime}. */
    public static StreamKey runtime(String projectId) {
        return new StreamKey(Type.RUNTIME, projectId);
    }

    /** Client-activity stream for a data source: {@code /data-sources/{id}/stream/clients}. */
    public static StreamKey clients(String dataSourceId) {
        return new StreamKey(Type.CLIENTS, dataSourceId);
    }

    /** Live-value stream for a data source: {@code /data-sources/{id}/stream/values}. */
    public static StreamKey values(String dataSourceId) {
        return new StreamKey(Type.VALUES, dataSourceId);
    }

    /** Scenario run progress stream: {@code /scenarios/{id}/runs/{runId}/events}. */
    public static StreamKey scenarioRun(String runId) {
        return new StreamKey(Type.SCENARIO_RUN, runId);
    }
}
