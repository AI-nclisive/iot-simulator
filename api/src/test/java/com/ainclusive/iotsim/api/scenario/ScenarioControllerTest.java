package com.ainclusive.iotsim.api.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ainclusive.iotsim.api.error.PreconditionRequiredException;
import com.ainclusive.iotsim.api.scenario.ScenarioController.CreateScenarioRequest;
import com.ainclusive.iotsim.api.scenario.ScenarioController.ScenarioResponse;
import com.ainclusive.iotsim.api.scenario.ScenarioController.StepDto;
import com.ainclusive.iotsim.api.scenario.ScenarioController.UpdateScenarioRequest;
import com.ainclusive.iotsim.api.stream.LiveStreamSubscriptions;
import com.ainclusive.iotsim.api.stream.StreamKey;
import com.ainclusive.iotsim.domain.activityevent.ActivityEventService;
import com.ainclusive.iotsim.domain.scenario.Scenario;
import com.ainclusive.iotsim.domain.scenario.ScenarioService;
import com.ainclusive.iotsim.domain.scenario.ScenarioStep;
import com.ainclusive.iotsim.persistence.activityevent.ActivityEventQuery;
import com.ainclusive.iotsim.persistence.activityevent.ActivityEventRepository;
import com.ainclusive.iotsim.persistence.activityevent.ActivityEventRow;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** POJO controller unit test (repo convention, cf. RuntimeStreamControllerTest): asserts
 *  status/headers and pre-delegation logic. HTTP-layer mapping is IS-121. */
class ScenarioControllerTest {

    private static Scenario sample(long version) {
        return new Scenario("scn-1", "p1", "Flow", "DRAFT", "{}",
                List.of(new ScenarioStep(0, "MARKER", null, "{}")),
                Instant.EPOCH, Instant.EPOCH, "local", version);
    }

    /** Capturing fake: overrides every method used; super-ctor args are unused. */
    private static final class FakeService extends ScenarioService {
        List<ScenarioStep> createdSteps;
        long updatedVersion = -1;
        boolean deleted;

        FakeService() {
            super(null, null, null, new ActivityEventService(new NoOpActivityEventRepository()));
        }

        @Override
        public Scenario create(String p, String n, String d, List<ScenarioStep> steps, String a) {
            this.createdSteps = steps;
            return sample(0);
        }

        @Override
        public Scenario get(String p, String id) {
            return sample(3);
        }

        @Override
        public Scenario update(String p, String id, String n, String d, List<ScenarioStep> steps, long ev) {
            this.updatedVersion = ev;
            return sample(ev + 1);
        }

        @Override
        public Scenario duplicate(String p, String id, String a) {
            return sample(0);
        }

        @Override
        public void delete(String p, String id, String actor) {
            this.deleted = true;
        }
    }

    @Test
    void createReturns201WithEtagLocationAndNormalizedOrdinals() {
        FakeService svc = new FakeService();
        var req = new CreateScenarioRequest("Flow", "{}",
                List.of(new StepDto("START", "ds-1", "{}"), new StepDto("STOP", "ds-1", "{}")));

        ResponseEntity<ScenarioResponse> resp = new ScenarioController(svc, null, null, null).create("p1", req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(resp.getHeaders().getLocation()).hasToString("/api/v1/projects/p1/scenarios/scn-1");
        assertThat(svc.createdSteps).extracting(ScenarioStep::ordinal).containsExactly(0, 1);
    }

    @Test
    void createWithBlankNameThrows() {
        ScenarioController c = new ScenarioController(new FakeService(), null, null, null);
        assertThatThrownBy(() -> c.create("p1", new CreateScenarioRequest("  ", "{}", List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getReturnsEtagFromVersion() {
        ResponseEntity<ScenarioResponse> resp = new ScenarioController(new FakeService(), null, null, null).get("p1", "scn-1");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"3\"");
    }

    @Test
    void updateWithoutIfMatchThrowsPreconditionRequired() {
        ScenarioController c = new ScenarioController(new FakeService(), null, null, null);
        assertThatThrownBy(() -> c.update("p1", "scn-1", null, new UpdateScenarioRequest("X", null, null)))
                .isInstanceOf(PreconditionRequiredException.class);
    }

    @Test
    void updateParsesIfMatchVersionAndReturnsBumpedEtag() {
        FakeService svc = new FakeService();
        ResponseEntity<ScenarioResponse> resp = new ScenarioController(svc, null, null, null)
                .update("p1", "scn-1", "\"5\"", new UpdateScenarioRequest("X", null, null));
        assertThat(svc.updatedVersion).isEqualTo(5);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"6\"");
    }

    @Test
    void updateWithNonNumericIfMatchThrows() {
        ScenarioController c = new ScenarioController(new FakeService(), null, null, null);
        assertThatThrownBy(() -> c.update("p1", "scn-1", "nope", new UpdateScenarioRequest("X", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteReturns204() {
        FakeService svc = new FakeService();
        ResponseEntity<Void> resp = new ScenarioController(svc, null, null, null).delete("p1", "scn-1");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(svc.deleted).isTrue();
    }

    @Test
    void duplicateReturns201WithLocation() {
        ResponseEntity<ScenarioResponse> resp = new ScenarioController(new FakeService(), null, null, null).duplicate("p1", "scn-1");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getHeaders().getLocation()).isNotNull();
    }

    @Test
    void streamRunEventsDelegatesToSubscriptions() {
        LiveStreamSubscriptions subscriptions = mock(LiveStreamSubscriptions.class);
        SseEmitter emitter = new SseEmitter();
        when(subscriptions.subscribe(eq(StreamKey.scenarioRun("run-1")), isNull())).thenReturn(emitter);

        ScenarioController c = new ScenarioController(null, null, null, subscriptions);
        SseEmitter result = c.streamRunEvents("p1", "scn-1", "run-1", null);

        assertThat(result).isSameAs(emitter);
    }

    private static final class NoOpActivityEventRepository implements ActivityEventRepository {
        @Override
        public ActivityEventRow append(String projectId, String actor, String action,
                String objectType, String objectId, String detailJson) {
            return new ActivityEventRow(0, projectId, actor, action, objectType, objectId,
                    OffsetDateTime.now(ZoneOffset.UTC), "{}");
        }

        @Override
        public List<ActivityEventRow> query(ActivityEventQuery filter) {
            return List.of();
        }
    }
}
