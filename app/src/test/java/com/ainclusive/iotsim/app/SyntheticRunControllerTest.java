package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ainclusive.iotsim.api.synthetic.SyntheticRunController;
import com.ainclusive.iotsim.api.synthetic.SyntheticRunController.SyntheticRunRequest;
import com.ainclusive.iotsim.api.synthetic.SyntheticRunController.SyntheticRunResponse;
import com.ainclusive.iotsim.domain.synthetic.SyntheticLiveRunService;
import com.ainclusive.iotsim.domain.synthetic.SyntheticLiveRunSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SyntheticRunControllerTest {

    private static final String PROJECT = "p1";
    private static final String SOURCE = "ds1";

    private SyntheticLiveRunService liveRuns;
    private SyntheticRunController controller;

    @BeforeEach
    void setUp() {
        liveRuns = mock(SyntheticLiveRunService.class);
        controller = new SyntheticRunController(liveRuns);
    }

    @Test
    void runReturnsRunningState() {
        when(liveRuns.start(
                org.mockito.ArgumentMatchers.eq(PROJECT),
                org.mockito.ArgumentMatchers.eq(SOURCE),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.eq("MANUAL"),
                org.mockito.ArgumentMatchers.eq("local")))
            .thenReturn(new SyntheticLiveRunSummary("ds1", 5L, "run-1", "ev-1", "RUNNING"));

        SyntheticRunResponse resp = controller.run(PROJECT, SOURCE, null);
        assertThat(resp.state()).isEqualTo("RUNNING");
        assertThat(resp.runId()).isEqualTo("run-1");
        assertThat(resp.valueCount()).isEqualTo(0L);
        assertThat(resp.seed()).isEqualTo(5L);
        assertThat(resp.evidenceId()).isEqualTo("ev-1");
    }

    @Test
    void runWithMaxDurationPropagatesCap() {
        when(liveRuns.start(
                org.mockito.ArgumentMatchers.eq(PROJECT),
                org.mockito.ArgumentMatchers.eq(SOURCE),
                org.mockito.ArgumentMatchers.eq(5000L),
                org.mockito.ArgumentMatchers.eq("MANUAL"),
                org.mockito.ArgumentMatchers.eq("local")))
            .thenReturn(new SyntheticLiveRunSummary("ds1", 7L, "run-2", "ev-2", "RUNNING"));

        SyntheticRunResponse resp = controller.run(PROJECT, SOURCE, new SyntheticRunRequest(5000L));
        assertThat(resp.state()).isEqualTo("RUNNING");
        assertThat(resp.runId()).isEqualTo("run-2");
        assertThat(resp.valueCount()).isEqualTo(0L);
    }

    @Test
    void invalidMaxDurationPropagatesException() {
        when(liveRuns.start(
                org.mockito.ArgumentMatchers.eq(PROJECT),
                org.mockito.ArgumentMatchers.eq(SOURCE),
                org.mockito.ArgumentMatchers.eq(0L),
                org.mockito.ArgumentMatchers.eq("MANUAL"),
                org.mockito.ArgumentMatchers.eq("local")))
            .thenThrow(new IllegalArgumentException("maxDurationMs must be > 0 when set: 0"));

        assertThatThrownBy(() -> controller.run(PROJECT, SOURCE, new SyntheticRunRequest(0L)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
