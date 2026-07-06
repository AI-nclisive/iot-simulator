package com.ainclusive.iotsim.api.scenario;

import com.ainclusive.iotsim.api.stream.LiveEventPublisher;
import com.ainclusive.iotsim.api.stream.StreamKey;
import com.ainclusive.iotsim.domain.scenario.ScenarioStepListener;
import java.time.Instant;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * Bridges ScenarioLiveRunService step events to the SSE registry (IS-142).
 * Implements the domain ScenarioStepListener port; injected into
 * ScenarioLiveRunService via @Autowired setter at startup.
 */
@Component
public class ScenarioRunStreamPublisher implements ScenarioStepListener {

    private final LiveEventPublisher publisher;

    public ScenarioRunStreamPublisher(LiveEventPublisher publisher) {
        this.publisher = publisher;
    }

    @Override
    public void onStepStarted(String projectId, String runId, int ordinal, String type) {
        publisher.publish(StreamKey.scenarioRun(runId), "step-started",
                Map.of("ordinal", ordinal, "type", type), Instant.now());
    }

    @Override
    public void onStepCompleted(String projectId, String runId, int ordinal, String type) {
        publisher.publish(StreamKey.scenarioRun(runId), "step-completed",
                Map.of("ordinal", ordinal, "type", type), Instant.now());
    }

    @Override
    public void onRunFinished(String projectId, String runId, String finalState) {
        publisher.publish(StreamKey.scenarioRun(runId), "run-finished",
                Map.of("state", finalState), Instant.now());
    }
}
