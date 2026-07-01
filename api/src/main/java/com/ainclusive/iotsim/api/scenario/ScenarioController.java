package com.ainclusive.iotsim.api.scenario;

import com.ainclusive.iotsim.api.error.PreconditionRequiredException;
import com.ainclusive.iotsim.domain.scenario.Scenario;
import com.ainclusive.iotsim.domain.scenario.ScenarioRunService;
import com.ainclusive.iotsim.domain.scenario.ScenarioRunSummary;
import com.ainclusive.iotsim.domain.scenario.ScenarioService;
import com.ainclusive.iotsim.domain.scenario.ScenarioStep;
import com.ainclusive.iotsim.domain.scenario.ScenarioValidation;
import com.ainclusive.iotsim.domain.scenario.ScenarioValidationService;
import com.ainclusive.iotsim.domain.scenario.StepOutcome;
import com.ainclusive.iotsim.domain.scenario.ValidationIssue;
import com.ainclusive.iotsim.domain.support.Page;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Scenario authoring CRUD within a project (backend-specs/05_API_CONTRACT.md, IS-085). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/scenarios")
public class ScenarioController {

    private final ScenarioService scenarios;
    private final ScenarioValidationService validationService;
    private final ScenarioRunService runService;

    public ScenarioController(ScenarioService scenarios,
            ScenarioValidationService validationService, ScenarioRunService runService) {
        this.scenarios = scenarios;
        this.validationService = validationService;
        this.runService = runService;
    }

    @GetMapping
    public Page<ScenarioResponse> list(
            @PathVariable String projectId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return scenarios.listPaged(projectId, cursor, limit).map(ScenarioResponse::from);
    }

    @PostMapping
    public ResponseEntity<ScenarioResponse> create(
            @PathVariable String projectId, @RequestBody CreateScenarioRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        Scenario s = scenarios.create(projectId, req.name(), req.deterministicSettings(),
                toSteps(req.steps()), "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/scenarios/" + s.id()))
                .eTag(etag(s.version()))
                .body(ScenarioResponse.from(s));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScenarioResponse> get(@PathVariable String projectId, @PathVariable String id) {
        Scenario s = scenarios.get(projectId, id);
        return ResponseEntity.ok().eTag(etag(s.version())).body(ScenarioResponse.from(s));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ScenarioResponse> update(
            @PathVariable String projectId,
            @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody UpdateScenarioRequest req) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException("If-Match header with the current version is required");
        }
        List<ScenarioStep> steps = req != null && req.steps() != null ? toSteps(req.steps()) : null;
        Scenario s = scenarios.update(projectId, id,
                req != null ? req.name() : null,
                req != null ? req.deterministicSettings() : null,
                steps, parseVersion(ifMatch));
        return ResponseEntity.ok().eTag(etag(s.version())).body(ScenarioResponse.from(s));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String projectId, @PathVariable String id) {
        scenarios.delete(projectId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ScenarioResponse> duplicate(
            @PathVariable String projectId, @PathVariable String id) {
        Scenario s = scenarios.duplicate(projectId, id, "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/scenarios/" + s.id()))
                .eTag(etag(s.version()))
                .body(ScenarioResponse.from(s));
    }

    @GetMapping("/{id}/validate")
    public ScenarioValidationResponse validate(@PathVariable String projectId, @PathVariable String id) {
        return ScenarioValidationResponse.from(validationService.validate(projectId, id));
    }

    @PostMapping("/{id}/run")
    public ScenarioRunResponse run(@PathVariable String projectId, @PathVariable String id,
            @RequestBody(required = false) RunScenarioRequest req) {
        String trigger = req != null ? req.trigger() : null;
        String initiator = req != null ? req.initiator() : null;
        return ScenarioRunResponse.from(runService.run(projectId, id, trigger, initiator));
    }

    private static List<ScenarioStep> toSteps(List<StepDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        List<ScenarioStep> out = new java.util.ArrayList<>();
        for (int i = 0; i < dtos.size(); i++) {
            StepDto d = dtos.get(i);
            out.add(new ScenarioStep(i, d.type(), d.targetSourceId(), d.params()));
        }
        return out;
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    private static long parseVersion(String ifMatch) {
        String v = ifMatch.trim();
        if (v.startsWith("W/")) {
            v = v.substring(2);
        }
        v = v.replace("\"", "").trim();
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid If-Match version: " + ifMatch);
        }
    }

    public record RunScenarioRequest(String trigger, String initiator) {}

    public record ValidationIssueResponse(int ordinal, String severity, String message) {
        static ValidationIssueResponse from(ValidationIssue i) {
            return new ValidationIssueResponse(i.ordinal(), i.severity(), i.message());
        }
    }

    public record ScenarioValidationResponse(String status, List<ValidationIssueResponse> issues) {
        static ScenarioValidationResponse from(ScenarioValidation v) {
            return new ScenarioValidationResponse(v.status(),
                    v.issues().stream().map(ValidationIssueResponse::from).toList());
        }
    }

    public record StepOutcomeResponse(int ordinal, String type, String childRunId, long applied, String state) {
        static StepOutcomeResponse from(StepOutcome o) {
            return new StepOutcomeResponse(o.ordinal(), o.type(), o.childRunId(), o.applied(), o.state());
        }
    }

    public record ScenarioRunResponse(String runId, String evidenceId, String status,
            List<StepOutcomeResponse> steps) {
        static ScenarioRunResponse from(ScenarioRunSummary s) {
            return new ScenarioRunResponse(s.runId(), s.evidenceId(), s.status(),
                    s.steps().stream().map(StepOutcomeResponse::from).toList());
        }
    }

    public record StepDto(String type, String targetSourceId, String params) {}

    public record CreateScenarioRequest(
            String name, String deterministicSettings, List<StepDto> steps) {}

    public record UpdateScenarioRequest(
            String name, String deterministicSettings, List<StepDto> steps) {}

    public record StepResponse(int ordinal, String type, String targetSourceId, String params) {
        static StepResponse from(ScenarioStep s) {
            return new StepResponse(s.ordinal(), s.type(), s.targetSourceId(), s.params());
        }
    }

    public record ScenarioResponse(
            String id,
            String projectId,
            String name,
            String status,
            String deterministicSettings,
            List<StepResponse> steps,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            long version) {

        static ScenarioResponse from(Scenario s) {
            return new ScenarioResponse(
                    s.id(), s.projectId(), s.name(), s.status(), s.deterministicSettings(),
                    s.steps().stream().map(StepResponse::from).toList(),
                    s.createdAt(), s.updatedAt(), s.createdBy(), s.version());
        }
    }
}
