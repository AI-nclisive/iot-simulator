package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ainclusive.iotsim.api.synthetic.SyntheticRunController;
import com.ainclusive.iotsim.api.synthetic.SyntheticRunController.SyntheticRunRequest;
import com.ainclusive.iotsim.api.synthetic.SyntheticRunController.SyntheticRunResponse;
import com.ainclusive.iotsim.domain.synthetic.SyntheticRunService;
import com.ainclusive.iotsim.domain.synthetic.SyntheticRunSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SyntheticRunControllerTest {

    private static final String PROJECT = "p1";
    private static final String SOURCE = "ds1";

    private SyntheticRunService service;
    private SyntheticRunController controller;

    @BeforeEach
    void setUp() {
        service = mock(SyntheticRunService.class);
        controller = new SyntheticRunController(service);
    }

    @Test
    void runReturnsSummary() {
        given(service.run(PROJECT, SOURCE, 1000L))
                .willReturn(new SyntheticRunSummary(SOURCE, 14, 5L, "run-1", "ev-1"));
        SyntheticRunResponse resp = controller.run(PROJECT, SOURCE, new SyntheticRunRequest(1000L));
        assertThat(resp.valueCount()).isEqualTo(14);
        assertThat(resp.seed()).isEqualTo(5L);
        assertThat(resp.runId()).isEqualTo("run-1");
        assertThat(resp.evidenceId()).isEqualTo("ev-1");
    }

    @Test
    void invalidDurationPropagatesBadRequest() {
        given(service.run(PROJECT, SOURCE, 0L))
                .willThrow(new IllegalArgumentException("durationMs must be > 0: 0"));
        assertThatThrownBy(() -> controller.run(PROJECT, SOURCE, new SyntheticRunRequest(0L)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
