package com.ainclusive.iotsim.api.scenario;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ainclusive.iotsim.api.stream.LiveEventPublisher;
import com.ainclusive.iotsim.api.stream.StreamKey;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ScenarioRunStreamPublisherTest {

    private final LiveEventPublisher publisher = mock(LiveEventPublisher.class);
    private final ScenarioRunStreamPublisher p = new ScenarioRunStreamPublisher(publisher);

    @Test
    void onStepStartedPublishesStepStartedEvent() {
        p.onStepStarted("proj", "run-1", 2, "START");
        verify(publisher).publish(eq(StreamKey.scenarioRun("run-1")), eq("step-started"),
                eq(Map.of("ordinal", 2, "type", "START")), any());
    }

    @Test
    void onStepCompletedPublishesStepCompletedEvent() {
        p.onStepCompleted("proj", "run-1", 2, "START");
        verify(publisher).publish(eq(StreamKey.scenarioRun("run-1")), eq("step-completed"),
                eq(Map.of("ordinal", 2, "type", "START")), any());
    }

    @Test
    void onRunFinishedPublishesRunFinishedEvent() {
        p.onRunFinished("proj", "run-1", "COMPLETED");
        verify(publisher).publish(eq(StreamKey.scenarioRun("run-1")), eq("run-finished"),
                eq(Map.of("state", "COMPLETED")), any());
    }
}
