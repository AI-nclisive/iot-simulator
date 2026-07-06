package com.ainclusive.iotsim.domain.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.replay.ReplayLiveRunService;
import com.ainclusive.iotsim.domain.replay.ReplayService;
import com.ainclusive.iotsim.domain.replay.ReplaySummary;
import com.ainclusive.iotsim.domain.scenario.ScenarioLiveRunService;
import com.ainclusive.iotsim.domain.scenario.ScenarioLiveRunSummary;

import com.ainclusive.iotsim.domain.synthetic.SyntheticLiveRunService;
import com.ainclusive.iotsim.domain.synthetic.SyntheticLiveRunSummary;
import com.ainclusive.iotsim.domain.synthetic.SyntheticRunService;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RunServiceTest {

    private static final String PROJECT = "p1";

    private RunRepository runs;
    private DataSourceRepository dataSources;
    private ScenarioRepository scenarios;
    private RuntimeController runtime;
    private ReplayService replay;
    private ReplayLiveRunService replayLive;
    private SyntheticRunService synthetic;
    private SyntheticLiveRunService syntheticLive;
    private ScenarioLiveRunService scenarioLive;
    private RunService service;

    @BeforeEach
    void setUp() {
        runs = mock(RunRepository.class);
        dataSources = mock(DataSourceRepository.class);
        scenarios = mock(ScenarioRepository.class);
        runtime = mock(RuntimeController.class);
        replay = mock(ReplayService.class);
        replayLive = mock(ReplayLiveRunService.class);
        synthetic = mock(SyntheticRunService.class);
        syntheticLive = mock(SyntheticLiveRunService.class);
        scenarioLive = mock(ScenarioLiveRunService.class);
        when(dataSources.findByProject(PROJECT)).thenReturn(List.of());
        service = new RunService(runs, dataSources, scenarios, runtime, replay, replayLive, synthetic,
                syntheticLive, scenarioLive);
    }

    private RunRow row(String id, String kind, String state, List<String> sources) {
        return new RunRow(id, PROJECT, kind, "MANUAL", "local", state, null, null,
                OffsetDateTime.now(), null, OffsetDateTime.now(), sources, null);
    }

    @Test
    void getMissingOrWrongProjectThrows404() {
        when(runs.findById("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.get(PROJECT, "nope")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void stateOfAggregatesRunStateAndPerSourceHealth() {
        when(runs.findById("r1")).thenReturn(Optional.of(row("r1", "REPLAY", "RUNNING", List.of("ds1"))));
        when(runtime.health("ds1")).thenReturn(new SourceHealth("RUNNING", null));
        RunState st = service.stateOf(PROJECT, "r1");
        assertThat(st.runState()).isEqualTo("RUNNING");
        assertThat(st.sources()).singleElement().satisfies(s -> {
            assertThat(s.sourceId()).isEqualTo("ds1");
            assertThat(s.state()).isEqualTo("RUNNING");
        });
    }

    @Test
    void stopStopsSourcesAndEndsRunWhenNonTerminal() {
        when(runs.findById("r1")).thenReturn(Optional.of(row("r1", "REPLAY", "RUNNING", List.of("ds1", "ds2"))));
        when(runs.end(eq("r1"), eq("STOPPED"), any())).thenReturn(row("r1", "REPLAY", "STOPPED", List.of("ds1", "ds2")));
        RunView v = service.stop(PROJECT, "r1");
        verify(runtime).stop("ds1");
        verify(runtime).stop("ds2");
        verify(runs).end(eq("r1"), eq("STOPPED"), any());
        assertThat(v.state()).isEqualTo("STOPPED");
    }

    @Test
    void stopTerminalRunStopsSourcesButDoesNotReEnd() {
        when(runs.findById("r1")).thenReturn(Optional.of(row("r1", "REPLAY", "COMPLETED", List.of("ds1"))));
        service.stop(PROJECT, "r1");
        verify(runtime).stop("ds1");
        org.mockito.Mockito.verify(runs, org.mockito.Mockito.never()).end(any(), any(), any());
    }

    @Test
    void startReplayRoutesWithAutomationTriggerAndInitiator() {
        when(replayLive.start(eq(PROJECT), eq("ds1"), eq("rec1"), any(), eq(false), eq("AUTOMATION"), eq("ci-bot")))
                .thenReturn(new ReplaySummary("rec1", "ds1", 5, "run-r", "ev", null));
        when(runs.findById("run-r")).thenReturn(Optional.of(row("run-r", "REPLAY", "RUNNING", List.of("ds1"))));
        RunView v = service.start(PROJECT, new StartRunCommand("REPLAY", "ci-bot", "ds1", "rec1", null, null, null, null, false));
        assertThat(v.id()).isEqualTo("run-r");
        assertThat(v.state()).isEqualTo("RUNNING");
        verify(replayLive).start(eq(PROJECT), eq("ds1"), eq("rec1"), any(), eq(false), eq("AUTOMATION"), eq("ci-bot"));
    }

    @Test
    void stopReplayCancelsLiveFeedAndEndsRunStopped() {
        when(runs.findById("run-r")).thenReturn(Optional.of(row("run-r", "REPLAY", "RUNNING", List.of("ds1"))));
        when(runs.end(eq("run-r"), eq("STOPPED"), any())).thenReturn(row("run-r", "REPLAY", "STOPPED", List.of("ds1")));
        service.stop(PROJECT, "run-r");
        verify(replayLive).stopIfLive("run-r");
        verify(runs).end(eq("run-r"), eq("STOPPED"), any());
    }

    @Test
    void startRejectsBlankInitiator() {
        assertThatThrownBy(() -> service.start(PROJECT, new StartRunCommand("REPLAY", "  ", "ds1", "rec1", null, null, null, null, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startRejectsUnknownKind() {
        assertThatThrownBy(() -> service.start(PROJECT, new StartRunCommand("NOPE", "ci-bot", null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ---- SYNTHETIC routing ----

    @Test
    void startSyntheticRoutesWithAutomationTriggerAndInitiator() {
        when(syntheticLive.start(eq(PROJECT), eq("ds1"), eq((Long) null), eq("AUTOMATION"), eq("ci-bot")))
                .thenReturn(new SyntheticLiveRunSummary("ds1", 7L, "run-9", "ev-9", "RUNNING"));
        when(runs.findById("run-9")).thenReturn(Optional.of(row("run-9", "SYNTHETIC", "RUNNING", List.of("ds1"))));
        RunView v = service.start(PROJECT, new StartRunCommand("SYNTHETIC", "ci-bot", "ds1",
                null, null, null, null, null, null));
        assertThat(v.id()).isEqualTo("run-9");
        assertThat(v.state()).isEqualTo("RUNNING");
        verify(syntheticLive).start(eq(PROJECT), eq("ds1"), eq((Long) null), eq("AUTOMATION"), eq("ci-bot"));
    }

    @Test
    void startSyntheticWithCapRoutesWithDurationMs() {
        when(syntheticLive.start(eq(PROJECT), eq("ds1"), eq(5000L), eq("AUTOMATION"), eq("ci-bot")))
                .thenReturn(new SyntheticLiveRunSummary("ds1", 7L, "run-9", "ev-9", "RUNNING"));
        when(runs.findById("run-9")).thenReturn(Optional.of(row("run-9", "SYNTHETIC", "RUNNING", List.of("ds1"))));
        RunView v = service.start(PROJECT, new StartRunCommand("SYNTHETIC", "ci-bot", "ds1", null, 5000L, null, null, null, null));
        assertThat(v.id()).isEqualTo("run-9");
        assertThat(v.state()).isEqualTo("RUNNING");
        verify(syntheticLive).start(eq(PROJECT), eq("ds1"), eq(5000L), eq("AUTOMATION"), eq("ci-bot"));
    }

    @Test
    void stopSyntheticCancelsLiveFeedAndEndsRunStopped() {
        when(runs.findById("run-9")).thenReturn(Optional.of(row("run-9", "SYNTHETIC", "RUNNING", List.of("ds1"))));
        when(runs.end(eq("run-9"), eq("STOPPED"), any())).thenReturn(row("run-9", "SYNTHETIC", "STOPPED", List.of("ds1")));
        service.stop(PROJECT, "run-9");
        verify(syntheticLive).stopIfLive("run-9");
        verify(runs).end(eq("run-9"), eq("STOPPED"), any());
    }

    // ---- SCENARIO routing ----

    @Test
    void startScenarioRoutesWithAutomationTriggerAndInitiator() {
        when(scenarioLive.start(eq(PROJECT), eq("sc1"), eq("AUTOMATION"), eq("ci-bot")))
                .thenReturn(new ScenarioLiveRunSummary("run-sc", "ev-sc"));
        when(runs.findById("run-sc")).thenReturn(Optional.of(row("run-sc", "SCENARIO", "RUNNING", List.of())));
        RunView v = service.start(PROJECT, new StartRunCommand("SCENARIO", "ci-bot", null, null, null, "sc1", null, null, null));
        assertThat(v.id()).isEqualTo("run-sc");
        verify(scenarioLive).start(eq(PROJECT), eq("sc1"), eq("AUTOMATION"), eq("ci-bot"));
    }

    // ---- per-kind field validation ----

    @Test
    void startReplayMissingDataSourceIdThrows() {
        assertThatThrownBy(() -> service.start(PROJECT,
                new StartRunCommand("REPLAY", "ci-bot", null, "rec1", null, null, null, null, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startReplayMissingRecordingIdThrows() {
        assertThatThrownBy(() -> service.start(PROJECT,
                new StartRunCommand("REPLAY", "ci-bot", "ds1", null, null, null, null, null, false)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startSyntheticMissingDataSourceIdThrows() {
        assertThatThrownBy(() -> service.start(PROJECT,
                new StartRunCommand("SYNTHETIC", "ci-bot", null, null, 5000L, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startSyntheticNullDurationMsIsUnboundedAndDoesNotThrow() {
        when(syntheticLive.start(eq(PROJECT), eq("ds1"), eq((Long) null), eq("AUTOMATION"), eq("ci-bot")))
                .thenReturn(new SyntheticLiveRunSummary("ds1", 7L, "run-u", "ev-u", "RUNNING"));
        when(runs.findById("run-u")).thenReturn(Optional.of(row("run-u", "SYNTHETIC", "RUNNING", List.of("ds1"))));
        // null durationMs is now valid (unbounded run) — must NOT throw
        RunView v = service.start(PROJECT,
                new StartRunCommand("SYNTHETIC", "ci-bot", "ds1", null, null, null, null, null, null));
        assertThat(v.state()).isEqualTo("RUNNING");
    }

    @Test
    void startSyntheticZeroDurationMsThrows() {
        assertThatThrownBy(() -> service.start(PROJECT,
                new StartRunCommand("SYNTHETIC", "ci-bot", "ds1", null, 0L, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void startScenarioMissingScenarioIdThrows() {
        assertThatThrownBy(() -> service.start(PROJECT,
                new StartRunCommand("SCENARIO", "ci-bot", null, null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
