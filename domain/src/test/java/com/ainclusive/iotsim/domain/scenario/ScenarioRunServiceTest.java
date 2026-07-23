package com.ainclusive.iotsim.domain.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class ScenarioRunServiceTest {

    private static final String PROJECT = "p1";
    private static final String SCENARIO = "scn-1";
    private static final String SOURCE = "ds-1";
    private static final String RECORDING = "rec-1";

    private ScenarioRepository scenarios;
    private ScenarioValidationService validation;
    private RunRepository runs;
    private EvidenceRepository evidence;
    private RuntimeEventRepository events;
    private DataSourceService dataSources;
    private ReplayService replay;
    private SyntheticRunService synthetic;
    private ScenarioRunService service;

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
        service = new ScenarioRunService(scenarios, validation, runs, evidence, events,
                dataSources, replay, synthetic, new ObjectMapper());
    }

    private void stubScenario(ScenarioStepRow... steps) {
        when(scenarios.findById(SCENARIO)).thenReturn(Optional.of(new ScenarioRow(
                SCENARIO, PROJECT, "Flow", "READY", "{}", List.of(steps),
                OffsetDateTime.now(), OffsetDateTime.now(), "local", 1)));
    }

    private void stubValidReady() {
        when(validation.validate(PROJECT, SCENARIO))
                .thenReturn(new ScenarioValidation("READY", List.of()));
    }

    private RunRow parentRun() {
        return new RunRow("run-parent", PROJECT, "SCENARIO", "MANUAL", "local", "RUNNING",
                SCENARIO, null, OffsetDateTime.now(), null, OffsetDateTime.now(), List.of(SOURCE), null);
    }

    @Test
    void faultStepWithInvalidParamsIsRejectedAsInvalid() {
        // FAULT step with null targetSourceId → validation returns INVALID → ScenarioInvalidException,
        // still without creating a run row.
        stubScenario(new ScenarioStepRow(0, "FAULT", null, "{}"));
        when(validation.validate(PROJECT, SCENARIO))
                .thenReturn(new ScenarioValidation("INVALID",
                        List.of(new ValidationIssue(0, "ERROR", "FAULT step requires a target source"))));
        assertThatThrownBy(() -> service.run(PROJECT, SCENARIO, "MANUAL", "local"))
                .isInstanceOf(ScenarioInvalidException.class);
        verify(runs, never()).create(anyString(), anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void validFaultStepInjectsAndReturnsOk() {
        stubScenario(new ScenarioStepRow(0, "FAULT", SOURCE, "{\"kind\":\"BAD_VALUE\",\"layer\":\"PROTOCOL\"}"));
        stubValidReady();
        RunRow parent = parentRun();
        when(runs.create(eq(PROJECT), eq("SCENARIO"), any(), any(), any(), eq(SCENARIO), eq((String) null)))
                .thenReturn(parent);
        when(runs.start(eq("run-parent"), any())).thenReturn(parent);
        RunRow completedParent = new RunRow("run-parent", PROJECT, "SCENARIO", "MANUAL", "local", "COMPLETED",
                SCENARIO, null, OffsetDateTime.now(), null, OffsetDateTime.now(), List.of(SOURCE), null);
        when(runs.end(eq("run-parent"), eq("COMPLETED"), any())).thenReturn(completedParent);
        when(evidence.create(eq(PROJECT), eq("run-parent"), anyString()))
                .thenReturn(new EvidenceRow("ev-1", PROJECT, "run-parent", "CAPTURING", "{}", null, null, "local"));

        ScenarioRunSummary summary = service.run(PROJECT, SCENARIO, "MANUAL", "local");

        assertThat(summary.status()).isEqualTo("COMPLETED");
        assertThat(summary.steps()).hasSize(1).first().satisfies(s -> {
            assertThat(s.type()).isEqualTo("FAULT");
            assertThat(s.state()).isEqualTo("OK");
        });
        verify(dataSources).injectFault(PROJECT, SOURCE, "BAD_VALUE", "PROTOCOL", true, java.util.Map.of());
    }

    @Test
    void invalidScenarioIsRejected() {
        stubScenario(new ScenarioStepRow(0, "START", "missing", "{}"));
        when(validation.validate(PROJECT, SCENARIO))
                .thenReturn(new ScenarioValidation("INVALID",
                        List.of(new ValidationIssue(0, "ERROR", "targetSourceId does not exist"))));
        assertThatThrownBy(() -> service.run(PROJECT, SCENARIO, "MANUAL", "local"))
                .isInstanceOf(ScenarioInvalidException.class);
        verify(runs, never()).create(anyString(), anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void happyPathRunsStepsUnderOneScenarioRunAndCompletes() {
        stubScenario(
                new ScenarioStepRow(0, "START", SOURCE, "{}"),
                new ScenarioStepRow(1, "REPLAY", SOURCE, "{\"recordingId\":\"" + RECORDING + "\"}"),
                new ScenarioStepRow(2, "STOP", SOURCE, "{}"));
        stubValidReady();
        RunRow parent = parentRun();
        when(runs.create(eq(PROJECT), eq("SCENARIO"), eq("MANUAL"), eq("local"), any(), eq(SCENARIO), eq((String) null)))
                .thenReturn(parent);
        when(runs.start(eq("run-parent"), any())).thenReturn(parent);
        when(runs.end(eq("run-parent"), anyString(), any())).thenReturn(parent);
        RunRow completedParent = new RunRow("run-parent", PROJECT, "SCENARIO", "MANUAL", "local", "COMPLETED",
                SCENARIO, null, OffsetDateTime.now(), null, OffsetDateTime.now(), List.of(SOURCE), null);
        when(runs.end(eq("run-parent"), eq("COMPLETED"), any())).thenReturn(completedParent);
        // EvidenceRow has 8 fields: (id, projectId, runId, status, manifestJson, objectRef, createdAt, createdBy)
        when(evidence.create(eq(PROJECT), eq("run-parent"), anyString()))
                .thenReturn(new EvidenceRow("ev-1", PROJECT, "run-parent", "CAPTURING", "{}", null, null, "local"));
        when(replay.replay(eq(PROJECT), eq(SOURCE), eq(RECORDING), any(), eq(false), eq("run-parent")))
                .thenReturn(new ReplaySummary(RECORDING, SOURCE, 42, "child-replay", "ev-2", null));

        ScenarioRunSummary summary = service.run(PROJECT, SCENARIO, "MANUAL", "local");

        assertThat(summary.runId()).isEqualTo("run-parent");
        assertThat(summary.status()).isEqualTo("COMPLETED"); // ended.state() from COMPLETED-state RunRow stub
        assertThat(summary.steps()).extracting(StepOutcome::type).containsExactly("START", "REPLAY", "STOP");
        verify(dataSources).start(eq(PROJECT), eq(SOURCE), anyString());
        verify(dataSources).stop(eq(PROJECT), eq(SOURCE), anyString());
        verify(replay).replay(eq(PROJECT), eq(SOURCE), eq(RECORDING), any(), eq(false), eq("run-parent"));
        verify(runs).end(eq("run-parent"), eq("COMPLETED"), any());

        ArgumentCaptor<String> manifests = ArgumentCaptor.forClass(String.class);
        verify(evidence, times(2)).updateManifest(eq("ev-1"), manifests.capture());
        assertThat(manifests.getAllValues().get(0)).contains("\"endedAt\":null");
        assertThat(manifests.getAllValues().get(1)).doesNotContain("\"endedAt\":null");
        assertThat(manifests.getAllValues().get(1)).contains("\"kind\":\"SCENARIO\"");
        // IS-182: scenario completion is mirrored into runtime_events (project-level, no single source).
        verify(events).append(eq(PROJECT), isNull(), eq("run-parent"), eq("RUN_COMPLETED"), any(), any());
    }

    @Test
    void malformedStartTimeThrowsBeforeCreatingRun() {
        // Scenario with a valid seed but unparseable startTime — parseSettings throws DateTimeParseException.
        // With Fix 1, parseSettings is called BEFORE runs.create, so no run row is ever created.
        ScenarioRow scenarioWithBadSettings = new ScenarioRow(
                SCENARIO, PROJECT, "Flow", "READY",
                "{\"seed\":123,\"startTime\":\"nope\"}",
                List.of(new ScenarioStepRow(0, "MARKER", null, "{}")),
                OffsetDateTime.now(), OffsetDateTime.now(), "local", 1);
        when(scenarios.findById(SCENARIO)).thenReturn(Optional.of(scenarioWithBadSettings));
        stubValidReady();

        assertThatThrownBy(() -> service.run(PROJECT, SCENARIO, "MANUAL", "local"))
                .isInstanceOf(RuntimeException.class);
        verify(runs, never()).create(anyString(), anyString(), any(), any(), any(), any(), any());
        verify(runs, never()).end(anyString(), anyString(), any());
    }

    @Test
    void markerAndWaitStepsExecuteAndEmitEvents() {
        // MARKER and WAIT steps have null targetSourceId — prove events.append is called with null
        // dataSourceId and does not throw (runtime_events.data_source_id is a nullable column).
        ScenarioRow scenarioWithMarkerWait = new ScenarioRow(
                SCENARIO, PROJECT, "Flow", "READY", "{}",
                List.of(
                        new ScenarioStepRow(0, "MARKER", null, "{\"label\":\"cp\"}"),
                        new ScenarioStepRow(1, "WAIT", null, "{\"durationMs\":1}")),
                OffsetDateTime.now(), OffsetDateTime.now(), "local", 1);
        when(scenarios.findById(SCENARIO)).thenReturn(Optional.of(scenarioWithMarkerWait));
        stubValidReady();
        RunRow parent = new RunRow("run-parent", PROJECT, "SCENARIO", "MANUAL", "local", "RUNNING",
                SCENARIO, null, OffsetDateTime.now(), null, OffsetDateTime.now(), List.of(), null);
        when(runs.create(eq(PROJECT), eq("SCENARIO"), any(), any(), any(), eq(SCENARIO), eq((String) null)))
                .thenReturn(parent);
        when(runs.start(eq("run-parent"), any())).thenReturn(parent);
        RunRow completedParent = new RunRow("run-parent", PROJECT, "SCENARIO", "MANUAL", "local", "COMPLETED",
                SCENARIO, null, OffsetDateTime.now(), null, OffsetDateTime.now(), List.of(), null);
        when(runs.end(eq("run-parent"), eq("COMPLETED"), any())).thenReturn(completedParent);
        when(evidence.create(eq(PROJECT), eq("run-parent"), anyString()))
                .thenReturn(new EvidenceRow("ev-1", PROJECT, "run-parent", "CAPTURING", "{}", null, null, "local"));

        ScenarioRunSummary summary = service.run(PROJECT, SCENARIO, "MANUAL", "local");

        assertThat(summary.status()).isEqualTo("COMPLETED");
        assertThat(summary.steps()).extracting(StepOutcome::type).containsExactly("MARKER", "WAIT");
        // Prove append is called with null dataSourceId for both steps — no throw, no guard needed.
        verify(events, times(2)).append(eq(PROJECT), isNull(), eq("run-parent"), eq("SCENARIO_STEP"), any(), any());
    }

    @Test
    void stepFailureEndsParentRunFailedAndSkipsRest() {
        stubScenario(
                new ScenarioStepRow(0, "START", SOURCE, "{}"),
                new ScenarioStepRow(1, "STOP", SOURCE, "{}"));
        stubValidReady();
        RunRow parent = parentRun();
        when(runs.create(eq(PROJECT), eq("SCENARIO"), any(), any(), any(), eq(SCENARIO), eq((String) null)))
                .thenReturn(parent);
        when(runs.start(anyString(), any())).thenReturn(parent);
        when(evidence.create(anyString(), anyString(), anyString()))
                .thenReturn(new EvidenceRow("ev-1", PROJECT, "run-parent", "CAPTURING", "{}", null, null, "local"));
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(dataSources).start(eq(PROJECT), eq(SOURCE), anyString());

        assertThatThrownBy(() -> service.run(PROJECT, SCENARIO, "MANUAL", "local"))
                .isInstanceOf(RuntimeException.class);
        verify(runs).end(eq("run-parent"), eq("FAILED"), any());
        verify(dataSources, never()).stop(eq(PROJECT), eq(SOURCE), anyString());

        ArgumentCaptor<String> manifests = ArgumentCaptor.forClass(String.class);
        verify(evidence, times(2)).updateManifest(eq("ev-1"), manifests.capture());
        assertThat(manifests.getAllValues().get(1)).doesNotContain("\"endedAt\":null");
        // IS-182: scenario failure is mirrored into runtime_events.
        verify(events).append(eq(PROJECT), isNull(), eq("run-parent"), eq("RUN_FAILED"), any(), any());
    }
}
