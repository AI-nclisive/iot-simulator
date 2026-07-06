package com.ainclusive.iotsim.domain.scenario;

/**
 * Port for scenario run progress events (IS-142). Implemented in the api layer
 * ({@code ScenarioRunStreamPublisher}) to publish to the SSE registry without
 * creating a domain→api dependency.
 */
public interface ScenarioStepListener {

    void onStepStarted(String projectId, String runId, int ordinal, String type);

    void onStepCompleted(String projectId, String runId, int ordinal, String type);

    void onRunFinished(String projectId, String runId, String finalState);

    /** Returns a no-op listener that silently discards all events. */
    static ScenarioStepListener noop() {
        return new ScenarioStepListener() {
            public void onStepStarted(String p, String r, int o, String t) {}
            public void onStepCompleted(String p, String r, int o, String t) {}
            public void onRunFinished(String p, String r, String s) {}
        };
    }
}
