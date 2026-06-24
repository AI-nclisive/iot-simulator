package com.epam.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.epam.iotsim.api.replay.ReplayController;
import com.epam.iotsim.api.replay.ReplayController.ReplayRequest;
import com.epam.iotsim.api.replay.ReplayController.ReplayResponse;
import com.epam.iotsim.domain.replay.ReplayService;
import com.epam.iotsim.domain.replay.ReplaySummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit test for {@link ReplayController}. */
class ReplayControllerTest {

    private ReplayService service;
    private ReplayController controller;

    @BeforeEach
    void setUp() {
        service = mock(ReplayService.class);
        controller = new ReplayController(service);
    }

    @Test
    void replayReturnsSummary() {
        given(service.replay("p1", "ds1", "rec1"))
                .willReturn(new ReplaySummary("rec1", "ds1", 5));
        ReplayResponse resp = controller.replay("p1", "ds1", new ReplayRequest("rec1"));
        assertThat(resp.recordingId()).isEqualTo("rec1");
        assertThat(resp.valueCount()).isEqualTo(5);
    }

    @Test
    void replayWithoutRecordingIdIsRejected() {
        assertThatThrownBy(() -> controller.replay("p1", "ds1", new ReplayRequest(" ")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
