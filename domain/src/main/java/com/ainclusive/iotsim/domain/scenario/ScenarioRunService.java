package com.ainclusive.iotsim.domain.scenario;

import com.ainclusive.iotsim.domain.common.FeatureNotAvailableException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.common.ScenarioInvalidException;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.replay.ReplayService;
import com.ainclusive.iotsim.domain.replay.ReplaySummary;
import com.ainclusive.iotsim.domain.synthetic.SyntheticRunService;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepRow;
import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Executes a scenario's ordered steps as one SCENARIO run (IS-086). Synchronous, fail-fast. */
@Service
public class ScenarioRunService {

    private static final long MAX_WAIT_MS = 60_000L;

    private final ScenarioRepository scenarios;
    private final ScenarioValidationService validation;
    private final RunRepository runs;
    private final EvidenceRepository evidence;
    private final RuntimeEventRepository events;
    private final DataSourceService dataSources;
    private final ReplayService replay;
    private final SyntheticRunService synthetic;
    private final ObjectMapper json;

    public ScenarioRunService(ScenarioRepository scenarios, ScenarioValidationService validation,
            RunRepository runs, EvidenceRepository evidence, RuntimeEventRepository events,
            DataSourceService dataSources, ReplayService replay, SyntheticRunService synthetic,
            ObjectMapper json) {
        this.scenarios = scenarios;
        this.validation = validation;
        this.runs = runs;
        this.evidence = evidence;
        this.events = events;
        this.dataSources = dataSources;
        this.replay = replay;
        this.synthetic = synthetic;
        this.json = json;
    }

    public ScenarioRunSummary run(String projectId, String scenarioId, String trigger, String initiator) {
        ScenarioRow scenario = scenarios.findById(scenarioId)
                .filter(s -> s.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Scenario", scenarioId));

        // Pre-flight, before any run row exists.
        if (scenario.steps().stream().anyMatch(s -> "FAULT".equals(s.type()))) {
            throw new FeatureNotAvailableException(
                    "scenario contains a FAULT step; fault injection is not available until IS-087/IS-088");
        }
        ScenarioValidation v = validation.validate(projectId, scenarioId);
        if (ScenarioValidation.INVALID.equals(v.status())) {
            throw new ScenarioInvalidException(scenarioId,
                    v.issues().stream().map(ValidationIssue::message).toList());
        }

        String trig = trigger != null ? trigger : "MANUAL";
        String actor = initiator != null && !initiator.isBlank() ? initiator : "local";
        List<String> sourceIds = List.copyOf(new LinkedHashSet<>(scenario.steps().stream()
                .map(ScenarioStepRow::targetSourceId).filter(s -> s != null && !s.isBlank()).toList()));

        RunRow run = runs.create(projectId, "SCENARIO", trig, actor, sourceIds, scenarioId, null);
        DeterministicSettings settings = parseSettings(scenario.deterministicSettings());
        List<StepOutcome> outcomes = new ArrayList<>();
        try {
            runs.start(run.id(), now());
            EvidenceRow ev = evidence.create(projectId, run.id(), actor);
            runs.linkEvidence(run.id(), ev.id());
            evidence.updateManifest(ev.id(), manifest(scenario));

            for (ScenarioStepRow step : scenario.steps()) {
                events.append(projectId, step.targetSourceId(), run.id(), "SCENARIO_STEP", now(),
                        stepPayload(step));
                outcomes.add(execute(projectId, step, run.id(), settings));
            }
            RunRow ended = runs.end(run.id(), "COMPLETED", now());
            return new ScenarioRunSummary(run.id(), ev.id(), ended.state(), outcomes);
        } catch (RuntimeException e) {
            runs.end(run.id(), "FAILED", now());
            throw e;
        }
    }

    private StepOutcome execute(String projectId, ScenarioStepRow step, String parentRunId,
            DeterministicSettings settings) {
        int at = step.ordinal();
        switch (step.type()) {
            case "START" -> {
                dataSources.start(projectId, step.targetSourceId());
                return new StepOutcome(at, "START", null, 0, "OK");
            }
            case "STOP" -> {
                dataSources.stop(projectId, step.targetSourceId());
                return new StepOutcome(at, "STOP", null, 0, "OK");
            }
            case "REPLAY" -> {
                ReplaySummary s = replay.replay(projectId, step.targetSourceId(),
                        text(step.params(), "recordingId"), settings,
                        bool(step.params(), "compatibilityAck"), parentRunId);
                return new StepOutcome(at, "REPLAY", s.runId(), s.valueCount(), "OK");
            }
            case "SYNTHETIC" -> {
                var s = synthetic.run(projectId, step.targetSourceId(),
                        longValue(step.params(), "durationMs"), parentRunId);
                return new StepOutcome(at, "SYNTHETIC", s.runId(), s.valueCount(), "OK");
            }
            case "WAIT" -> {
                sleep(Math.min(longValue(step.params(), "durationMs"), MAX_WAIT_MS));
                return new StepOutcome(at, "WAIT", null, 0, "OK");
            }
            case "MARKER" -> {
                return new StepOutcome(at, "MARKER", null, 0, "OK");
            }
            default -> throw new IllegalArgumentException("unexecutable step type: " + step.type());
        }
    }

    private DeterministicSettings parseSettings(String detJson) {
        Long seed = longValueNullable(detJson, "seed");
        if (seed == null) {
            return null;
        }
        String startTime = text(detJson, "startTime");
        Instant start = startTime != null ? Instant.parse(startTime) : Instant.now();
        return new DeterministicSettings(seed, start);
    }

    private String manifest(ScenarioRow scenario) {
        return json.writeValueAsString(Map.of(
                "scenario", true,
                "scenarioId", scenario.id(),
                "name", scenario.name(),
                "stepCount", scenario.steps().size()));
    }

    private String stepPayload(ScenarioStepRow step) {
        String label = text(step.params(), "label");
        return json.writeValueAsString(Map.of(
                "ordinal", step.ordinal(),
                "type", step.type(),
                "label", label != null ? label : ""));
    }

    // ---- small JSON param readers (tolerant: return null/false on absence/parse failure) ----

    private String text(String jsonStr, String field) {
        JsonNode n = node(jsonStr, field);
        return n != null && n.isString() ? n.asString() : null;
    }

    private long longValue(String jsonStr, String field) {
        JsonNode n = node(jsonStr, field);
        return n != null && n.isNumber() ? n.asLong() : 0L;
    }

    private Long longValueNullable(String jsonStr, String field) {
        JsonNode n = node(jsonStr, field);
        return n != null && n.isNumber() ? n.asLong() : null;
    }

    private boolean bool(String jsonStr, String field) {
        JsonNode n = node(jsonStr, field);
        return n != null && n.isBoolean() && n.asBoolean();
    }

    private JsonNode node(String jsonStr, String field) {
        if (jsonStr == null || jsonStr.isBlank()) {
            return null;
        }
        try {
            JsonNode root = json.readTree(jsonStr);
            return root.isObject() ? root.get(field) : null;
        } catch (JacksonException e) {
            return null;
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("scenario WAIT interrupted", e);
        }
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
