package com.ainclusive.iotsim.api.run;

import com.ainclusive.iotsim.domain.run.RunService;
import com.ainclusive.iotsim.domain.run.RunState;
import com.ainclusive.iotsim.domain.run.RunView;
import com.ainclusive.iotsim.domain.run.SourceState;
import com.ainclusive.iotsim.domain.run.StartRunCommand;
import com.ainclusive.iotsim.domain.support.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/** Unified runs resource + test-control for automation (backend-specs/05, IS-089). */
@RestController
@Tag(name = "Runs")
@RequestMapping("/api/v1/projects/{projectId}/runs")
public class RunController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";
    private static final String REPLAY_START =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).REPLAY_START)";
    private static final String REPLAY_STOP =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).REPLAY_STOP)";

    private final RunService runs;

    public RunController(RunService runs) {
        this.runs = runs;
    }

    @Operation(summary = "List runs",
            description = "Returns runs in the project using cursor-based pagination (cursor and limit).")
    @GetMapping
    @PreAuthorize(OBSERVE)
    public Page<RunResponse> list(@PathVariable String projectId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return runs.listPaged(projectId, cursor, limit).map(RunResponse::from);
    }

    @Operation(summary = "Get a run",
            description = "Returns a single run by id within the project.")
    @GetMapping("/{id}")
    @PreAuthorize(OBSERVE)
    public RunResponse get(@PathVariable String projectId, @PathVariable String id) {
        return RunResponse.from(runs.get(projectId, id));
    }

    @Operation(summary = "Get live run state",
            description = "Returns the live run state along with the per-source state for each source in the run.")
    @GetMapping("/{id}/state")
    @PreAuthorize(OBSERVE)
    public RunStateResponse state(@PathVariable String projectId, @PathVariable String id) {
        return RunStateResponse.from(runs.stateOf(projectId, id));
    }

    @Operation(summary = "Stop a run",
            description = "Stops the given run and returns its updated state.")
    @PostMapping("/{id}/stop")
    @PreAuthorize(REPLAY_STOP)
    public RunResponse stop(@PathVariable String projectId, @PathVariable String id) {
        return RunResponse.from(runs.stop(projectId, id));
    }

    @Operation(summary = "Start a run",
            description = "Starts a run of the given kind (the kind field is required). Responds 201 Created with a"
                    + " Location header pointing to the new run.")
    @PostMapping
    @PreAuthorize(REPLAY_START)
    public ResponseEntity<RunResponse> start(@PathVariable String projectId, @RequestBody StartRunRequest req) {
        if (req == null || req.kind() == null || req.kind().isBlank()) {
            throw new IllegalArgumentException("kind is required");
        }
        RunView v = runs.start(projectId, new StartRunCommand(req.kind(), req.initiator(),
                req.dataSourceId(), req.recordingId(), req.durationMs(), req.scenarioId(),
                req.seed(), req.startTime(), req.compatibilityAck()));
        return ResponseEntity.created(
                        UriComponentsBuilder.fromPath("/api/v1/projects/{projectId}/runs/{id}")
                                .buildAndExpand(projectId, v.id()).toUri())
                .body(RunResponse.from(v));
    }

    public record StartRunRequest(String kind, String initiator, String dataSourceId, String recordingId,
            Long durationMs, String scenarioId, Long seed, String startTime, Boolean compatibilityAck) {}

    public record RunResponse(String id, String projectId, String kind, String trigger, String initiator,
            String state, String scenarioId, String evidenceId, String parentRunId, List<String> sourceIds,
            Instant startedAt, Instant endedAt, Instant createdAt, String label, String relatedLabel) {
        static RunResponse from(RunView v) {
            return new RunResponse(v.id(), v.projectId(), v.kind(), v.trigger(), v.initiator(), v.state(),
                    v.scenarioId(), v.evidenceId(), v.parentRunId(), v.sourceIds(),
                    v.startedAt(), v.endedAt(), v.createdAt(), v.label(), v.relatedLabel());
        }
    }

    public record SourceStateResponse(String sourceId, String state, String lastError) {
        static SourceStateResponse from(SourceState s) {
            return new SourceStateResponse(s.sourceId(), s.state(), s.lastError());
        }
    }

    public record RunStateResponse(String runState, List<SourceStateResponse> sources) {
        static RunStateResponse from(RunState st) {
            return new RunStateResponse(st.runState(), st.sources().stream().map(SourceStateResponse::from).toList());
        }
    }
}
