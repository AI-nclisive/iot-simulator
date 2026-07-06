package com.ainclusive.iotsim.domain.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ainclusive.iotsim.domain.common.FeatureNotAvailableException;
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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * Unit tests for {@link ScenarioLiveRunService}. Uses a direct (synchronous) executor so step
 * execution completes inline and tests need no real threading.
 */
class ScenarioLiveRunServiceTest {

    private static final String PROJECT = "p1";
    private static final String SCENARIO = "scn-1";
    private static final String SOURCE = "ds-1";

    private ScenarioRepository scenarios;
    private ScenarioValidationService validation;
    private RunRepository runs;
    private EvidenceRepository evidence;
    private RuntimeEventRepository events;
    private DataSourceService dataSources;
    private ReplayService replay;
    private ScenarioStepListener stepListener;
    private SyntheticRunService synthetic;
    private ScenarioLiveRunService service;

    /** Synchronous executor: runs tasks on the calling thread so tests are deterministic. */
    private static final ExecutorService DIRECT = new AbstractExecutorService() {
        public void execute(Runnable command) { command.run(); }
        public void shutdown() {}
        public List<Runnable> shutdownNow() { return List.of(); }
        public boolean isShutdown() { return false; }
        public boolean isTerminated() { return false; }
        public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
    };

    @BeforeEach
    void setUp() {
        scenarios = mock(ScenarioRepository.class);
        validation = mock(ScenarioValidationService.class);
        runs = mock(RunRepository.class);
        evidence = mock(EvidenceRepository.class);
        events = mock(RuntimeEventRepository.class);
        dataSources = mock(DataSourceService.class);
        replay = mock(ReplayService.class);
        synthetic = mock(SyntheticRunService.class);
        stepListener = mock(ScenarioStepListener.class);
        service = new ScenarioLiveRunService(scenarios, validation, runs, evidence, events,
                dataSources, replay, synthetic, new ObjectMapper(), DIRECT);
        service.setStepListener(stepListener);
    }

    // ---- helpers ----

    private void stubScenario(ScenarioStepRow... steps) {
        when(scenarios.findById(SCENARIO)).thenReturn(Optional.of(new ScenarioRow(
                SCENARIO, PROJECT, "Flow", "READY", "{}", List.of(steps),
                OffsetDateTime.now(), OffsetDateTime.now(), "local", 1)));
    }

    private void stubValidReady() {
        when(validation.validate(PROJECT, SCENARIO))
                .thenReturn(new ScenarioValidation("READY", List.of()));
    }

    private RunRow runningRun(String id) {
        return new RunRow(id, PROJECT, "SCENARIO", "MANUAL", "local", "RUNNING",
                SCENARIO, null, OffsetDateTime.now(), null, OffsetDateTime.now(), List.of(SOURCE), null);
    }

    private void stubRunLifecycle(String runId) {
        RunRow parent = runningRun(runId);
        when(runs.create(eq(PROJECT), eq("SCENARIO"), any(), any(), any(), eq(SCENARIO), eq((String) null)))
                .thenReturn(parent);
        when(runs.start(eq(runId), any())).thenReturn(parent);
        RunRow completed = new RunRow(runId, PROJECT, "SCENARIO", "MANUAL", "local", "COMPLETED",
                SCENARIO, null, OffsetDateTime.now(), null, OffsetDateTime.now(), List.of(SOURCE), null);
        when(runs.end(eq(runId), eq("COMPLETED"), any())).thenReturn(completed);
        when(evidence.create(eq(PROJECT), eq(runId), anyString()))
                .thenReturn(new EvidenceRow("ev-1", PROJECT, runId, "CAPTURING", "{}", null, null, "local"));
    }

    // ---- tests ----

    @Test
    void faultStepIsRejectedWithoutCreatingARun() {
        stubScenario(new ScenarioStepRow(0, "FAULT", null, "{}"));
        assertThatThrownBy(() -> service.start(PROJECT, SCENARIO, "MANUAL", "local"))
                .isInstanceOf(FeatureNotAvailableException.class);
        verify(runs, never()).create(anyString(), anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void invalidScenarioIsRejected() {
        stubScenario(new ScenarioStepRow(0, "START", "missing", "{}"));
        when(validation.validate(PROJECT, SCENARIO))
                .thenReturn(new ScenarioValidation("INVALID",
                        List.of(new ValidationIssue(0, "ERROR", "targetSourceId does not exist"))));
        assertThatThrownBy(() -> service.start(PROJECT, SCENARIO, "MANUAL", "local"))
                .isInstanceOf(ScenarioInvalidException.class);
        verify(runs, never()).create(anyString(), anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void startReturnsRunIdAndEvidenceId() {
        // With the DIRECT executor, steps execute synchronously inside start(), but start() still
        // returns the ScenarioLiveRunSummary with the correct runId/evidenceId.
        stubScenario(new ScenarioStepRow(0, "MARKER", null, "{}"));
        stubValidReady();
        stubRunLifecycle("run-1");

        ScenarioLiveRunSummary summary = service.start(PROJECT, SCENARIO, "MANUAL", "local");

        assertThat(summary.runId()).isEqualTo("run-1");
        assertThat(summary.evidenceId()).isEqualTo("ev-1");
    }

    @Test
    void allStepsCompleteRunEndsCompleted() {
        stubScenario(
                new ScenarioStepRow(0, "START", SOURCE, "{}"),
                new ScenarioStepRow(1, "STOP", SOURCE, "{}"));
        stubValidReady();
        stubRunLifecycle("run-1");

        service.start(PROJECT, SCENARIO, "MANUAL", "local");

        // With DIRECT executor, executeSteps ran synchronously — verify terminal state.
        verify(runs).end(eq("run-1"), eq("COMPLETED"), any());
        verify(dataSources).start(PROJECT, SOURCE);
        verify(dataSources).stop(PROJECT, SOURCE);
    }

    @Test
    void stepFailureEndsRunFailed() {
        stubScenario(new ScenarioStepRow(0, "START", SOURCE, "{}"));
        stubValidReady();
        RunRow parent = runningRun("run-1");
        when(runs.create(any(), any(), any(), any(), any(), any(), any())).thenReturn(parent);
        when(runs.start(eq("run-1"), any())).thenReturn(parent);
        when(evidence.create(any(), eq("run-1"), any()))
                .thenReturn(new EvidenceRow("ev-1", PROJECT, "run-1", "CAPTURING", "{}", null, null, "local"));
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(dataSources).start(PROJECT, SOURCE);

        // start() itself should not throw (the exception is caught inside executeSteps)
        ScenarioLiveRunSummary summary = service.start(PROJECT, SCENARIO, "MANUAL", "local");

        assertThat(summary.runId()).isEqualTo("run-1");
        verify(runs).end(eq("run-1"), eq("FAILED"), any());
    }

    @Test
    void stopReturnsTrueWhenRunIsLiveAndFalseWhenNotFound() {
        // Use a non-executing executor so the future stays in the registry.
        ExecutorService nonExecuting = new AbstractExecutorService() {
            public void execute(Runnable command) { /* don't run */ }
            public void shutdown() {}
            public List<Runnable> shutdownNow() { return List.of(); }
            public boolean isShutdown() { return false; }
            public boolean isTerminated() { return false; }
            public boolean awaitTermination(long timeout, TimeUnit unit) { return true; }
        };
        ScenarioLiveRunService svc = new ScenarioLiveRunService(scenarios, validation, runs, evidence,
                events, dataSources, replay, synthetic, new ObjectMapper(), nonExecuting);

        stubScenario(new ScenarioStepRow(0, "MARKER", null, "{}"));
        stubValidReady();
        RunRow parent = runningRun("run-2");
        when(runs.create(any(), any(), any(), any(), any(), any(), any())).thenReturn(parent);
        when(runs.start(eq("run-2"), any())).thenReturn(parent);
        when(evidence.create(any(), eq("run-2"), any()))
                .thenReturn(new EvidenceRow("ev-2", PROJECT, "run-2", "CAPTURING", "{}", null, null, "local"));

        svc.start(PROJECT, SCENARIO, "MANUAL", "local");

        // run-2 is in the registry (task was submitted but not executed)
        assertThat(svc.stopIfLive("run-2")).isTrue();
        // idempotent: second call returns false
        assertThat(svc.stopIfLive("run-2")).isFalse();
        // unknown runId
        assertThat(svc.stopIfLive("does-not-exist")).isFalse();
    }

    @Test
    void stopAfterCompletionReturnsFalse() {
        // With DIRECT executor, steps run and complete synchronously during start().
        // The finally block in executeSteps removes the runId from the registry.
        stubScenario(new ScenarioStepRow(0, "MARKER", null, "{}"));
        stubValidReady();
        stubRunLifecycle("run-3");

        ScenarioLiveRunSummary summary = service.start(PROJECT, SCENARIO, "MANUAL", "local");

        // After completion the registry is empty, so stopIfLive returns false (idempotent).
        assertThat(service.stopIfLive(summary.runId())).isFalse();
    }

    @Test
    void waitStepWithShortDurationCompletesWithoutBlocking() {
        stubScenario(new ScenarioStepRow(0, "WAIT", null, "{\"durationMs\":1}"));
        stubValidReady();
        stubRunLifecycle("run-4");

        ScenarioLiveRunSummary summary = service.start(PROJECT, SCENARIO, "MANUAL", "local");

        assertThat(summary.runId()).isEqualTo("run-4");
        verify(runs).end(eq("run-4"), eq("COMPLETED"), any());
    }

    @Test
    void teardownStopsSourcesThatWereStartedWhenRunFails() {
        // Scenario: START(src-A), then SYNTHETIC throws → FAILED path.
        // Teardown must stop src-A because it was started but never stopped within the scenario.
        stubScenario(
                new ScenarioStepRow(0, "START", SOURCE, "{}"),
                new ScenarioStepRow(1, "SYNTHETIC", SOURCE, "{\"durationMs\":0}"));
        stubValidReady();
        RunRow parent = runningRun("run-5");
        when(runs.create(any(), any(), any(), any(), any(), any(), any())).thenReturn(parent);
        when(runs.start(eq("run-5"), any())).thenReturn(parent);
        when(evidence.create(any(), eq("run-5"), any()))
                .thenReturn(new EvidenceRow("ev-5", PROJECT, "run-5", "CAPTURING", "{}", null, null, "local"));
        org.mockito.Mockito.doThrow(new RuntimeException("synthetic boom"))
                .when(synthetic).run(eq(PROJECT), eq(SOURCE), any(Long.class), anyString());

        service.start(PROJECT, SCENARIO, "MANUAL", "local");

        verify(runs).end(eq("run-5"), eq("FAILED"), any());
        // Teardown must have stopped src-A (once via teardown, not once via STOP step)
        verify(dataSources).start(PROJECT, SOURCE);
        verify(dataSources).stop(PROJECT, SOURCE);
    }

    @Test
    void teardownDoesNotStopSourcesAlreadyStoppedByScenario() {
        // Scenario: START(src-A), STOP(src-A) — both succeed → COMPLETED.
        // Teardown must NOT add an extra stop call since src-A was removed from startedSources
        // when the STOP step executed.
        stubScenario(
                new ScenarioStepRow(0, "START", SOURCE, "{}"),
                new ScenarioStepRow(1, "STOP", SOURCE, "{}"));
        stubValidReady();
        stubRunLifecycle("run-6");

        service.start(PROJECT, SCENARIO, "MANUAL", "local");

        verify(runs).end(eq("run-6"), eq("COMPLETED"), any());
        verify(dataSources).start(PROJECT, SOURCE);
        // stop() called exactly once — by the explicit STOP step, not by teardown
        verify(dataSources, times(1)).stop(PROJECT, SOURCE);
    }

    @Test
    void teardownIsSkippedOnCompletedRun() {
        // A scenario with no explicit STOP steps that completes normally.
        // dataSources.stop() must never be called (teardown is COMPLETED-path exempt).
        stubScenario(
                new ScenarioStepRow(0, "START", SOURCE, "{}"),
                new ScenarioStepRow(1, "MARKER", null, "{}"));
        stubValidReady();
        RunRow parent = runningRun("run-7");
        when(runs.create(any(), any(), any(), any(), any(), any(), any())).thenReturn(parent);
        when(runs.start(eq("run-7"), any())).thenReturn(parent);
        RunRow completed = new RunRow("run-7", PROJECT, "SCENARIO", "MANUAL", "local", "COMPLETED",
                SCENARIO, null, OffsetDateTime.now(), null, OffsetDateTime.now(), List.of(SOURCE), null);
        when(runs.end(eq("run-7"), eq("COMPLETED"), any())).thenReturn(completed);
        when(evidence.create(any(), eq("run-7"), any()))
                .thenReturn(new EvidenceRow("ev-7", PROJECT, "run-7", "CAPTURING", "{}", null, null, "local"));

        service.start(PROJECT, SCENARIO, "MANUAL", "local");

        verify(runs).end(eq("run-7"), eq("COMPLETED"), any());
        verify(dataSources).start(PROJECT, SOURCE);
        // No stop() call at all — COMPLETED path skips teardown
        verify(dataSources, never()).stop(any(), any());
    }

    @Test
    void teardownStopsStartedSourcesWhenInterruptedBetweenSteps() {
        // Scenario: START(src-A) then WAIT(long). dataSources.start() sets the thread interrupt flag
        // so the next loop iteration's Thread.interrupted() check fires → STOPPED path → teardown.
        stubScenario(
                new ScenarioStepRow(0, "START", SOURCE, "{}"),
                new ScenarioStepRow(1, "WAIT", null, "{\"durationMs\":60000}"));
        stubValidReady();
        RunRow parent = runningRun("run-9");
        when(runs.create(any(), any(), any(), any(), any(), any(), any())).thenReturn(parent);
        when(runs.start(eq("run-9"), any())).thenReturn(parent);
        when(evidence.create(any(), eq("run-9"), any()))
                .thenReturn(new EvidenceRow("ev-9", PROJECT, "run-9", "CAPTURING", "{}", null, null, "local"));
        // Interrupt the thread after the START step so WAIT's loop-top check detects it.
        org.mockito.Mockito.doAnswer(inv -> { Thread.currentThread().interrupt(); return null; })
                .when(dataSources).start(PROJECT, SOURCE);

        service.start(PROJECT, SCENARIO, "MANUAL", "local");

        verify(runs).end(eq("run-9"), eq("STOPPED"), any());
        // src-A was added to startedSources before the interrupt; teardown must stop it.
        verify(dataSources).stop(PROJECT, SOURCE);
        verify(stepListener).onRunFinished(PROJECT, "run-9", "STOPPED");
    }

    @Test
    void teardownToleratesStopFailuresAndContinues() {
        // Scenario: START(src-A), START(src-B), then step fails → FAILED + teardown of both.
        // If stop(src-A) throws, teardown must still attempt stop(src-B).
        String sourceB = "ds-2";
        stubScenario(
                new ScenarioStepRow(0, "START", SOURCE, "{}"),
                new ScenarioStepRow(1, "START", sourceB, "{}"),
                new ScenarioStepRow(2, "SYNTHETIC", SOURCE, "{\"durationMs\":0}"));
        stubValidReady();
        RunRow parent = runningRun("run-8");
        when(runs.create(any(), any(), any(), any(), any(), any(), any())).thenReturn(parent);
        when(runs.start(eq("run-8"), any())).thenReturn(parent);
        when(evidence.create(any(), eq("run-8"), any()))
                .thenReturn(new EvidenceRow("ev-8", PROJECT, "run-8", "CAPTURING", "{}", null, null, "local"));
        org.mockito.Mockito.doThrow(new RuntimeException("synthetic boom"))
                .when(synthetic).run(eq(PROJECT), eq(SOURCE), any(Long.class), anyString());
        // stop(src-A) throws during teardown
        org.mockito.Mockito.doThrow(new RuntimeException("stop failed"))
                .when(dataSources).stop(PROJECT, SOURCE);

        service.start(PROJECT, SCENARIO, "MANUAL", "local");

        verify(runs).end(eq("run-8"), eq("FAILED"), any());
        // src-A stop was attempted (even though it threw)
        verify(dataSources).stop(PROJECT, SOURCE);
        // src-B stop was also attempted despite src-A's failure
        verify(dataSources).stop(PROJECT, sourceB);
    }
}
