package com.ainclusive.iotsim.domain.scenario;

import com.ainclusive.iotsim.domain.activityevent.ActivityEventService;
import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.domain.support.PageCursor;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepInput;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Scenario authoring CRUD (IS-085). Validation is model-level only; cross-entity checks
 *  and run execution belong to IS-086. */
@Service
public class ScenarioService {

    static final Set<String> STEP_TYPES =
            Set.of("START", "STOP", "REPLAY", "SYNTHETIC", "FAULT", "WAIT", "MARKER");
    private static final Set<String> TARGET_REQUIRED = Set.of("START", "STOP");

    private final ScenarioRepository scenarios;
    private final ProjectRepository projects;
    private final ObjectMapper json;
    private final ActivityEventService activity;

    public ScenarioService(ScenarioRepository scenarios, ProjectRepository projects, ObjectMapper json,
            ActivityEventService activity) {
        this.scenarios = scenarios;
        this.projects = projects;
        this.json = json;
        this.activity = activity;
    }

    @Transactional
    public Scenario create(String projectId, String name, String deterministicSettings,
            List<ScenarioStep> steps, String actor) {
        requireProject(projectId);
        requireName(name);
        String det = normalizeJsonObject(deterministicSettings, "deterministicSettings");
        List<ScenarioStepInput> inputs = validateAndMap(steps);
        ScenarioRow row = scenarios.create(projectId, name, det, inputs, actor);
        activity.emit(projectId, actor, "create", "scenario", row.id());
        return map(row);
    }

    public Page<Scenario> listPaged(String projectId, String cursor, Integer limit) {
        requireProject(projectId);
        int size = PageCursor.clamp(limit);
        PageCursor.Parts after = PageCursor.decode(cursor);
        OffsetDateTime afterAt = after != null ? after.at() : null;
        String afterId = after != null ? after.id() : null;
        List<ScenarioRow> rows = scenarios.findByProjectPaged(projectId, afterAt, afterId, size + 1);
        String nextCursor = null;
        if (rows.size() > size) {
            rows = rows.subList(0, size);
            ScenarioRow last = rows.get(rows.size() - 1);
            nextCursor = PageCursor.encode(last.createdAt(), last.id());
        }
        return new Page<>(rows.stream().map(this::map).toList(), nextCursor, size);
    }

    public Scenario get(String projectId, String id) {
        return map(requireScenario(projectId, id));
    }

    public Scenario update(String projectId, String id, String name, String deterministicSettings,
            List<ScenarioStep> steps, long expectedVersion) {
        requireScenario(projectId, id);
        String validatedName = null;
        if (name != null) {
            requireName(name);
            validatedName = name;
        }
        String det = deterministicSettings != null
                ? normalizeJsonObject(deterministicSettings, "deterministicSettings")
                : null;
        List<ScenarioStepInput> inputs = steps != null ? validateAndMap(steps) : null;
        return scenarios.update(id, validatedName, det, inputs, expectedVersion)
                .map(this::map)
                .orElseThrow(() -> new ConcurrencyConflictException("Scenario", id, expectedVersion));
    }

    public Scenario duplicate(String projectId, String id, String actor) {
        ScenarioRow src = requireScenario(projectId, id);
        List<ScenarioStepInput> copied = src.steps().stream()
                .map(s -> new ScenarioStepInput(s.type(), s.targetSourceId(), s.params()))
                .toList();
        return map(scenarios.create(projectId, src.name() + " (copy)",
                src.deterministicSettings(), copied, actor));
    }

    @Transactional
    public void delete(String projectId, String id, String actor) {
        requireScenario(projectId, id);
        scenarios.deleteById(id);
        activity.emit(projectId, actor, "delete", "scenario", id);
    }

    // ---- helpers ----

    private void requireProject(String projectId) {
        if (projects.findById(projectId).isEmpty()) {
            throw new ResourceNotFoundException("Project", projectId);
        }
    }

    private ScenarioRow requireScenario(String projectId, String id) {
        return scenarios.findById(id)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Scenario", id));
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
    }

    private List<ScenarioStepInput> validateAndMap(List<ScenarioStep> steps) {
        if (steps == null) {
            return List.of();
        }
        return steps.stream().map(s -> {
            if (s.type() == null || !STEP_TYPES.contains(s.type())) {
                throw new IllegalArgumentException("invalid step type: " + s.type());
            }
            if (TARGET_REQUIRED.contains(s.type()) && (s.targetSourceId() == null || s.targetSourceId().isBlank())) {
                throw new IllegalArgumentException(s.type() + " step requires targetSourceId");
            }
            String params = normalizeJsonObject(s.params(), "step params");
            return new ScenarioStepInput(s.type(), s.targetSourceId(), params);
        }).toList();
    }

    /** Ensures the value is a JSON object; null/blank → "{}". Throws on invalid/non-object JSON. */
    private String normalizeJsonObject(String value, String field) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        try {
            JsonNode node = json.readTree(value);
            if (!node.isObject()) {
                throw new IllegalArgumentException(field + " must be a JSON object");
            }
        } catch (JacksonException e) {
            throw new IllegalArgumentException(field + " must be valid JSON: " + e.getMessage());
        }
        return value;
    }

    private Scenario map(ScenarioRow r) {
        List<ScenarioStep> steps = r.steps().stream()
                .map(s -> new ScenarioStep(s.ordinal(), s.type(), s.targetSourceId(), s.params()))
                .toList();
        return new Scenario(r.id(), r.projectId(), r.name(), r.status(), r.deterministicSettings(),
                steps, r.createdAt().toInstant(), r.updatedAt().toInstant(), r.createdBy(), r.version());
    }
}
