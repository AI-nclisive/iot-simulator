package com.ainclusive.iotsim.domain.scenario;

import com.ainclusive.iotsim.domain.common.FeatureNotAvailableException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.common.ScenarioInvalidException;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.replay.ReplayService;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Async scenario execution engine (IS-141). {@code start()} returns a {@link ScenarioLiveRunSummary}
 * immediately with a runId; the steps execute in a background daemon thread. {@code stopIfLive()}
 * cancels a running execution by interrupting its thread (which propagates through any WAIT sleep)
 * and integrates with {@code RunService.stop()} via the same {@code stopIfLive} convention used by
 * {@code SyntheticLiveRunService} and {@code ReplayLiveRunService}.
 */
@Service
public class ScenarioLiveRunService {

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
    private final ExecutorService executor;

    private final ConcurrentMap<String, ScenarioExecution> registry = new ConcurrentHashMap<>();

    @Autowired
    public ScenarioLiveRunService(ScenarioRepository scenarios, ScenarioValidationService validation,
            RunRepository runs, EvidenceRepository evidence, RuntimeEventRepository events,
            DataSourceService dataSources, ReplayService replay, SyntheticRunService synthetic,
            ObjectMapper json) {
        this(scenarios, validation, runs, evidence, events, dataSources, replay, synthetic, json,
                Executors.newCachedThreadPool(r -> {
                    Thread t = new Thread(r, "scenario-runner");
                    t.setDaemon(true);
                    return t;
                }));
    }

    /** Test seam: inject a controllable executor (e.g. direct/synchronous) so tests avoid real threading. */
    ScenarioLiveRunService(ScenarioRepository scenarios, ScenarioValidationService validation,
            RunRepository runs, EvidenceRepository evidence, RuntimeEventRepository events,
            DataSourceService dataSources, ReplayService replay, SyntheticRunService synthetic,
            ObjectMapper json, ExecutorService executor) {
        this.scenarios = scenarios;
        this.validation = validation;
        this.runs = runs;
        this.evidence = evidence;
        this.events = events;
        this.dataSources = dataSources;
        this.replay = replay;
        this.synthetic = synthetic;
        this.json = json;
        this.executor = executor;
    }

    /**
     * Starts an async scenario execution. Validates the scenario, creates the run + evidence rows,
     * submits step execution to the background executor, and returns immediately.
     */
    public ScenarioLiveRunSummary start(String projectId, String scenarioId, String trigger, String initiator) {
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

        DeterministicSettings settings = parseSettings(scenario.deterministicSettings());
        RunRow run = runs.create(projectId, "SCENARIO", trig, actor, sourceIds, scenarioId, null);
        try {
            runs.start(run.id(), now());
            EvidenceRow ev = evidence.create(projectId, run.id(), actor);
            runs.linkEvidence(run.id(), ev.id());
            evidence.updateManifest(ev.id(), manifest(scenario));

            // Capture final locals for the lambda
            final String runId = run.id();
            final String evidenceId = ev.id();
            final DeterministicSettings finalSettings = settings;

            // Register a placeholder before submitting so the registry entry exists before the
            // task can run (important for synchronous test executors and stop-before-start races).
            // The placeholder's null future is replaced atomically after submit() returns.
            registry.put(runId, new ScenarioExecution(runId, evidenceId, null));

            Future<?> future = executor.submit(() ->
                    executeSteps(projectId, runId, scenario, finalSettings));

            // Replace the placeholder with the real future. If stopIfLive() removed the
            // placeholder between registry.put and here, computeIfPresent is a no-op and
            // stillLive is false — cancel the just-submitted task immediately.
            boolean stillLive = registry.computeIfPresent(runId,
                    (k, old) -> new ScenarioExecution(runId, evidenceId, future)) != null;
            if (!stillLive) {
                future.cancel(true);
            }
            return new ScenarioLiveRunSummary(runId, evidenceId);
        } catch (RuntimeException e) {
            runs.end(run.id(), "FAILED", now());
            throw e;
        }
    }

    /**
     * Cancels a running scenario if it is currently in the registry. Interrupts the background
     * thread (which propagates through WAIT sleeps). The background thread is responsible for
     * ending the run in STOPPED state. Returns whether a live execution was actually cancelled
     * (idempotent: returns false if already completed or never started).
     *
     * <p>NOTE: Unlike {@code SyntheticLiveRunService.stopIfLive}, scenario runs manage their own
     * run-end transition (the background thread writes STOPPED/FAILED/COMPLETED). The run may
     * momentarily still appear RUNNING immediately after this call returns; {@code RunService.stop}
     * will then write STOPPED unconditionally (idempotent since TERMINAL states are not re-written).
     */
    public boolean stopIfLive(String runId) {
        ScenarioExecution execution = registry.remove(runId);
        if (execution == null) {
            return false;
        }
        // future may be null if stop races the placeholder window before submit() returns
        if (execution.future() != null) {
            execution.future().cancel(true);
        }
        return true;
    }

    // ---- background step execution ----

    private void executeSteps(String projectId, String runId, ScenarioRow scenario,
            DeterministicSettings settings) {
        try {
            for (ScenarioStepRow step : scenario.steps()) {
                if (Thread.interrupted()) {
                    safeEnd(runId, "STOPPED");
                    registry.remove(runId);
                    return;
                }
                // WAIT/MARKER have no targetSourceId; only append events for steps with a source
                if (step.targetSourceId() != null && !step.targetSourceId().isBlank()) {
                    events.append(projectId, step.targetSourceId(), runId, "SCENARIO_STEP", now(),
                            stepPayload(step));
                }
                execute(projectId, step, runId, settings);
            }
            runs.end(runId, "COMPLETED", now());
        } catch (IllegalStateException e) {
            // WAIT sleep interrupted — Thread.currentThread().interrupt() was called in sleep()
            if (Thread.currentThread().isInterrupted() || e.getCause() instanceof InterruptedException) {
                safeEnd(runId, "STOPPED");
            } else {
                safeEnd(runId, "FAILED");
            }
        } catch (RuntimeException e) {
            safeEnd(runId, "FAILED");
        } finally {
            registry.remove(runId);
        }
    }

    /**
     * Best-effort terminal-state write. A DB failure during the error-handling path must
     * not escape the background thread or prevent registry cleanup (done in finally).
     */
    private void safeEnd(String runId, String state) {
        try {
            runs.end(runId, state, now());
        } catch (RuntimeException ignored) {
            // intentionally swallowed — registry cleanup still happens in finally
        }
    }

    private void execute(String projectId, ScenarioStepRow step, String parentRunId,
            DeterministicSettings settings) {
        switch (step.type()) {
            case "START" -> dataSources.start(projectId, step.targetSourceId());
            case "STOP" -> dataSources.stop(projectId, step.targetSourceId());
            case "REPLAY" -> replay.replay(projectId, step.targetSourceId(),
                    text(step.params(), "recordingId"), settings,
                    bool(step.params(), "compatibilityAck"), parentRunId);
            case "SYNTHETIC" -> synthetic.run(projectId, step.targetSourceId(),
                    longValue(step.params(), "durationMs"), parentRunId);
            case "WAIT" -> sleep(Math.min(longValue(step.params(), "durationMs"), MAX_WAIT_MS));
            case "MARKER" -> { /* no-op */ }
            default -> throw new IllegalArgumentException("unexecutable step type: " + step.type());
        }
    }

    // ---- helpers ----

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

    /** In-memory handle for one running async scenario execution. */
    record ScenarioExecution(String runId, String evidenceId, Future<?> future) {}
}
