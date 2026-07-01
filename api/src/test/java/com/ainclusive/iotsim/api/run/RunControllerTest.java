package com.ainclusive.iotsim.api.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.api.run.RunController.RunResponse;
import com.ainclusive.iotsim.api.run.RunController.RunStateResponse;
import com.ainclusive.iotsim.api.run.RunController.StartRunRequest;
import com.ainclusive.iotsim.domain.run.RunService;
import com.ainclusive.iotsim.domain.run.RunState;
import com.ainclusive.iotsim.domain.run.RunView;
import com.ainclusive.iotsim.domain.run.SourceState;
import com.ainclusive.iotsim.domain.run.StartRunCommand;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

class RunControllerTest {

    private static RunView view(String id, String state) {
        return new RunView(id, "p1", "REPLAY", "AUTOMATION", "ci-bot", state, null, "ev", null,
                List.of("ds1"), Instant.EPOCH, null, Instant.EPOCH, "src replay", "src");
    }

    private static final class FakeService extends RunService {
        String startedKind;
        FakeService() { super(null, null, null, null, null, null, null); }
        @Override public RunView get(String p, String id) { return view(id, "RUNNING"); }
        @Override public RunState stateOf(String p, String id) {
            return new RunState("RUNNING", List.of(new SourceState("ds1", "RUNNING", null)));
        }
        @Override public RunView stop(String p, String id) { return view(id, "STOPPED"); }
        @Override public RunView start(String p, StartRunCommand cmd) { this.startedKind = cmd.kind(); return view("run-new", "COMPLETED"); }
    }

    @Test
    void getMapsRun() {
        RunResponse r = new RunController(new FakeService()).get("p1", "r1");
        assertThat(r.id()).isEqualTo("r1");
        assertThat(r.trigger()).isEqualTo("AUTOMATION");
    }

    @Test
    void stateMapsRunAndSources() {
        RunStateResponse r = new RunController(new FakeService()).state("p1", "r1");
        assertThat(r.runState()).isEqualTo("RUNNING");
        assertThat(r.sources()).singleElement().satisfies(s -> assertThat(s.sourceId()).isEqualTo("ds1"));
    }

    @Test
    void stopMapsStoppedRun() {
        assertThat(new RunController(new FakeService()).stop("p1", "r1").state()).isEqualTo("STOPPED");
    }

    @Test
    void startReturns201AndRoutesKind() {
        FakeService svc = new FakeService();
        ResponseEntity<RunResponse> resp = new RunController(svc).start("p1",
                new StartRunRequest("REPLAY", "ci-bot", "ds1", "rec1", null, null, null, null, false));
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getHeaders().getLocation()).hasToString("/api/v1/projects/p1/runs/run-new");
        assertThat(svc.startedKind).isEqualTo("REPLAY");
    }

    @Test
    void startWithBlankKindThrows() {
        assertThatThrownBy(() -> new RunController(new FakeService()).start("p1",
                new StartRunRequest("  ", "ci-bot", null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
