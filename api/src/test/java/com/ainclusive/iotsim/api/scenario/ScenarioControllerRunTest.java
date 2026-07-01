package com.ainclusive.iotsim.api.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.api.scenario.ScenarioController.ScenarioRunResponse;
import com.ainclusive.iotsim.api.scenario.ScenarioController.ScenarioValidationResponse;
import com.ainclusive.iotsim.domain.scenario.ScenarioRunService;
import com.ainclusive.iotsim.domain.scenario.ScenarioRunSummary;
import com.ainclusive.iotsim.domain.scenario.ScenarioValidation;
import com.ainclusive.iotsim.domain.scenario.ScenarioValidationService;
import com.ainclusive.iotsim.domain.scenario.StepOutcome;
import com.ainclusive.iotsim.domain.scenario.ValidationIssue;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioControllerRunTest {

    private static final class FakeValidation extends ScenarioValidationService {
        FakeValidation() { super(null, null, null, null, null); }
        @Override public ScenarioValidation validate(String p, String id) {
            return new ScenarioValidation("INVALID", List.of(new ValidationIssue(0, "ERROR", "bad")));
        }
    }

    private static final class FakeRun extends ScenarioRunService {
        FakeRun() { super(null, null, null, null, null, null, null, null, null); }
        @Override public ScenarioRunSummary run(String p, String id, String trigger, String initiator) {
            return new ScenarioRunSummary("run-1", "ev-1", "COMPLETED",
                    List.of(new StepOutcome(0, "MARKER", null, 0, "OK")));
        }
    }

    @Test
    void validateMapsStatusAndIssues() {
        ScenarioController c = new ScenarioController(null, new FakeValidation(), new FakeRun());
        ScenarioValidationResponse resp = c.validate("p1", "scn-1");
        assertThat(resp.status()).isEqualTo("INVALID");
        assertThat(resp.issues()).singleElement()
                .satisfies(i -> assertThat(i.message()).isEqualTo("bad"));
    }

    @Test
    void runMapsSummary() {
        ScenarioController c = new ScenarioController(null, new FakeValidation(), new FakeRun());
        ScenarioRunResponse resp = c.run("p1", "scn-1", null);
        assertThat(resp.runId()).isEqualTo("run-1");
        assertThat(resp.status()).isEqualTo("COMPLETED");
        assertThat(resp.steps()).singleElement().satisfies(s -> assertThat(s.type()).isEqualTo("MARKER"));
    }
}
