package com.ainclusive.iotsim.api.scenario;

import com.ainclusive.iotsim.api.error.PreconditionRequiredException;
import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.scenario.Scenario;
import com.ainclusive.iotsim.domain.scenario.ScenarioService;
import com.ainclusive.iotsim.domain.scenario.ScenarioStep;
import com.ainclusive.iotsim.domain.support.Page;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

/**
 * Scenario authoring CRUD within a project (backend-specs/05_API_CONTRACT.md, IS-085).
 *
 * <p>Authorization (IS-077): list/get — {@link Permission#OBSERVE};
 * create/update/delete/duplicate — {@link Permission#SCENARIO_EDIT} (admin).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/scenarios")
public class ScenarioController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";
    private static final String SCENARIO_EDIT =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).SCENARIO_EDIT)";

    private final ScenarioService scenarios;

    public ScenarioController(ScenarioService scenarios) {
        this.scenarios = scenarios;
    }

    @GetMapping
    @PreAuthorize(OBSERVE)
    public Page<ScenarioResponse> list(
            @PathVariable String projectId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return scenarios.listPaged(projectId, cursor, limit).map(ScenarioResponse::from);
    }

    @PostMapping
    @PreAuthorize(SCENARIO_EDIT)
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
    @PreAuthorize(OBSERVE)
    public ResponseEntity<ScenarioResponse> get(@PathVariable String projectId, @PathVariable String id) {
        Scenario s = scenarios.get(projectId, id);
        return ResponseEntity.ok().eTag(etag(s.version())).body(ScenarioResponse.from(s));
    }

    @PatchMapping("/{id}")
    @PreAuthorize(SCENARIO_EDIT)
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
    @PreAuthorize(SCENARIO_EDIT)
    public ResponseEntity<Void> delete(@PathVariable String projectId, @PathVariable String id) {
        scenarios.delete(projectId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    @PreAuthorize(SCENARIO_EDIT)
    public ResponseEntity<ScenarioResponse> duplicate(
            @PathVariable String projectId, @PathVariable String id) {
        Scenario s = scenarios.duplicate(projectId, id, "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/scenarios/" + s.id()))
                .eTag(etag(s.version()))
                .body(ScenarioResponse.from(s));
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
