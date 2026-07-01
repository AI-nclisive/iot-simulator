package com.ainclusive.iotsim.domain.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.replay.ReplayService;
import com.ainclusive.iotsim.domain.replay.ReplaySummary;
import com.ainclusive.iotsim.domain.scenario.ScenarioRunService;
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
    private SyntheticRunService synthetic;
    private ScenarioRunService scenarioRun;
    private RunService service;

    @BeforeEach
    void setUp() {
        runs = mock(RunRepository.class);
        dataSources = mock(DataSourceRepository.class);
        scenarios = mock(ScenarioRepository.class);
        runtime = mock(RuntimeController.class);
        replay = mock(ReplayService.class);
        synthetic = mock(SyntheticRunService.class);
        scenarioRun = mock(ScenarioRunService.class);
        when(dataSources.findByProject(PROJECT)).thenReturn(List.of());
        service = new RunService(runs, dataSources, scenarios, runtime, replay, synthetic, scenarioRun);
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
        when(replay.replay(eq(PROJECT), eq("ds1"), eq("rec1"), any(), eq(false), eq("AUTOMATION"), eq("ci-bot"), eq((String) null)))
                .thenReturn(new ReplaySummary("rec1", "ds1", 5, "run-r", "ev", null));
        when(runs.findById("run-r")).thenReturn(Optional.of(row("run-r", "REPLAY", "COMPLETED", List.of("ds1"))));
        RunView v = service.start(PROJECT, new StartRunCommand("REPLAY", "ci-bot", "ds1", "rec1", null, null, null, null, false));
        assertThat(v.id()).isEqualTo("run-r");
        verify(replay).replay(eq(PROJECT), eq("ds1"), eq("rec1"), any(), eq(false), eq("AUTOMATION"), eq("ci-bot"), eq((String) null));
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
}
