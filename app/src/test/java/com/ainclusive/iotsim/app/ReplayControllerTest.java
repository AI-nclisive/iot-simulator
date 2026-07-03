package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ainclusive.iotsim.api.replay.ReplayController;
import com.ainclusive.iotsim.api.replay.ReplayController.ReplayRequest;
import com.ainclusive.iotsim.api.replay.ReplayController.ReplayResponse;
import com.ainclusive.iotsim.domain.replay.ReplayLiveRunService;
import com.ainclusive.iotsim.domain.replay.ReplaySummary;
import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit test for {@link ReplayController}. */
class ReplayControllerTest {

    private ReplayLiveRunService service;
    private ReplayController controller;

    @BeforeEach
    void setUp() {
        service = mock(ReplayLiveRunService.class);
        controller = new ReplayController(service);
    }

    @Test
    void replayReturnsSummary() {
        DeterministicSettings settings = new DeterministicSettings(42L, Instant.parse("2026-01-01T00:00:00Z"));
        given(service.start("p1", "ds1", "rec1", null, false))
                .willReturn(new ReplaySummary("rec1", "ds1", 5, "run-1", "ev-1", settings));
        ReplayResponse resp = controller.replay("p1", "ds1", new ReplayRequest("rec1", null, null, null));
        assertThat(resp.recordingId()).isEqualTo("rec1");
        assertThat(resp.valueCount()).isEqualTo(5);
        assertThat(resp.runId()).isEqualTo("run-1");
        assertThat(resp.evidenceId()).isEqualTo("ev-1");
        assertThat(resp.seed()).isEqualTo(42L);
    }

    @Test
    void replayWithExplicitSeedPassesSettingsToService() {
        DeterministicSettings settings = new DeterministicSettings(99L, Instant.parse("2026-06-01T00:00:00Z"));
        given(service.start("p1", "ds1", "rec1", settings, true))
                .willReturn(new ReplaySummary("rec1", "ds1", 2, "run-2", "ev-2", settings));
        ReplayResponse resp = controller.replay("p1", "ds1",
                new ReplayRequest("rec1", 99L, Instant.parse("2026-06-01T00:00:00Z"), true));
        assertThat(resp.seed()).isEqualTo(99L);
    }

    @Test
    void replayWithoutRecordingIdIsRejected() {
        assertThatThrownBy(() -> controller.replay("p1", "ds1", new ReplayRequest(" ", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replaySeedWithoutStartTimeIsRejected() {
        assertThatThrownBy(() -> controller.replay("p1", "ds1",
                new ReplayRequest("rec1", 42L, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startTime");
    }

    @Test
    void replayStartTimeWithoutSeedIsRejected() {
        assertThatThrownBy(() -> controller.replay("p1", "ds1",
                new ReplayRequest("rec1", null, Instant.parse("2026-01-01T00:00:00Z"), null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("seed");
    }
}
