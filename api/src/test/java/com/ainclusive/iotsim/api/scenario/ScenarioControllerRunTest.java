package com.ainclusive.iotsim.api.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ainclusive.iotsim.api.scenario.ScenarioController.ScenarioLiveRunResponse;
import com.ainclusive.iotsim.api.scenario.ScenarioController.ScenarioValidationResponse;
import com.ainclusive.iotsim.api.stream.LiveStreamSubscriptions;
import com.ainclusive.iotsim.api.stream.StreamKey;
import com.ainclusive.iotsim.domain.scenario.ScenarioLiveRunService;
import com.ainclusive.iotsim.domain.scenario.ScenarioLiveRunSummary;
import com.ainclusive.iotsim.domain.scenario.ScenarioValidation;
import com.ainclusive.iotsim.domain.scenario.ScenarioValidationService;
import com.ainclusive.iotsim.domain.scenario.ValidationIssue;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ScenarioControllerRunTest {

    private static final class FakeValidation extends ScenarioValidationService {
        FakeValidation() { super(null, null, null, null, null); }
        @Override public ScenarioValidation validate(String p, String id) {
            return new ScenarioValidation("INVALID", List.of(new ValidationIssue(0, "ERROR", "bad")));
        }
    }

    private static final class FakeLiveRun extends ScenarioLiveRunService {
        FakeLiveRun() { super(null, null, null, null, null, null, null, null, null); }
        @Override public ScenarioLiveRunSummary start(String p, String id, String trigger, String initiator) {
            return new ScenarioLiveRunSummary("run-1", "ev-1");
        }
    }

    @Test
    void validateMapsStatusAndIssues() {
        ScenarioController c = new ScenarioController(null, new FakeValidation(), new FakeLiveRun(), null);
        ScenarioValidationResponse resp = c.validate("p1", "scn-1");
        assertThat(resp.status()).isEqualTo("INVALID");
        assertThat(resp.issues()).singleElement()
                .satisfies(i -> assertThat(i.message()).isEqualTo("bad"));
    }

    @Test
    void runReturnsSummary() {
        ScenarioController c = new ScenarioController(null, null, new FakeLiveRun(), null);
        ScenarioLiveRunResponse resp = c.run("p1", "scn-1", null);
        assertThat(resp.runId()).isEqualTo("run-1");
        assertThat(resp.evidenceId()).isEqualTo("ev-1");
    }

    @Test
    void streamRunEventsDelegatesToSubscriptions() {
        LiveStreamSubscriptions subscriptions = mock(LiveStreamSubscriptions.class);
        SseEmitter expected = new SseEmitter();
        when(subscriptions.subscribe(StreamKey.scenarioRun("run-1"), "last-42"))
                .thenReturn(expected);
        ScenarioController c = new ScenarioController(null, null, null, subscriptions);
        SseEmitter actual = c.streamRunEvents("p1", "scn-1", "run-1", "last-42");
        assertThat(actual).isSameAs(expected);
    }
}
