package com.ainclusive.iotsim.api.activerun;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.api.activerun.ActiveRunController.ActiveRunResponse;
import com.ainclusive.iotsim.api.activerun.ActiveRunController.ActiveRunsResponse;
import com.ainclusive.iotsim.domain.activerun.ActiveRun;
import com.ainclusive.iotsim.domain.activerun.ActiveRunService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * POJO controller unit test (repo convention, cf. ScenarioControllerTest):
 * asserts delegation and wire-shape mapping; no Spring context.
 */
class ActiveRunControllerTest {

    /** Minimal fake service that returns a fixed list of active runs. */
    private static final class FakeService extends ActiveRunService {
        private final List<ActiveRun> result;

        FakeService(List<ActiveRun> result) {
            super(null, null, null);
            this.result = result;
        }

        @Override
        public List<ActiveRun> getActiveRuns(String projectId) {
            return result;
        }
    }

    private static ActiveRun recording() {
        return new ActiveRun("run-1", "Sensor capture Recording", "Recording", "running",
                Instant.parse("2026-07-01T14:31:00Z"), "alice", "src-01", "Sensor capture");
    }

    private static ActiveRun replay() {
        return new ActiveRun("run-2", "Batch replay Replay", "Replay", "queued",
                null, "local", "src-02", "Batch stream");
    }

    private static ActiveRun scenario() {
        return new ActiveRun("run-3", "Smoke test", "Scenario", "running",
                Instant.parse("2026-07-01T14:00:00Z"), "ci-pipeline", null, "Smoke test");
    }

    @Test
    void emptyProjectReturnsEmptyItems() {
        ActiveRunController c = new ActiveRunController(new FakeService(List.of()));
        ActiveRunsResponse resp = c.list("p1");
        assertThat(resp.items()).isEmpty();
    }

    @Test
    void responseWrapsAllActiveRuns() {
        ActiveRunController c = new ActiveRunController(
                new FakeService(List.of(recording(), replay(), scenario())));
        ActiveRunsResponse resp = c.list("p1");
        assertThat(resp.items()).hasSize(3);
    }

    @Test
    void recordingRunMappedCorrectly() {
        ActiveRunController c = new ActiveRunController(new FakeService(List.of(recording())));
        ActiveRunResponse item = c.list("p1").items().get(0);

        assertThat(item.id()).isEqualTo("run-1");
        assertThat(item.label()).isEqualTo("Sensor capture Recording");
        assertThat(item.processType()).isEqualTo("Recording");
        assertThat(item.runState()).isEqualTo("running");
        assertThat(item.startedAt()).isEqualTo("2026-07-01T14:31:00Z");
        assertThat(item.initiator()).isEqualTo("alice");
        assertThat(item.relatedSourceId()).isEqualTo("src-01");
        assertThat(item.relatedLabel()).isEqualTo("Sensor capture");
    }

    @Test
    void queuedRunWithNullStartedAtSerializesAsNull() {
        ActiveRunController c = new ActiveRunController(new FakeService(List.of(replay())));
        ActiveRunResponse item = c.list("p1").items().get(0);

        assertThat(item.runState()).isEqualTo("queued");
        assertThat(item.startedAt()).isNull();
    }

    @Test
    void scenarioRunHasNullRelatedSourceId() {
        ActiveRunController c = new ActiveRunController(new FakeService(List.of(scenario())));
        ActiveRunResponse item = c.list("p1").items().get(0);

        assertThat(item.processType()).isEqualTo("Scenario");
        assertThat(item.relatedSourceId()).isNull();
        assertThat(item.relatedLabel()).isEqualTo("Smoke test");
    }
}
