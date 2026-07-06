package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ainclusive.iotsim.api.recording.RecordingCaptureController;
import com.ainclusive.iotsim.api.recording.RecordingController.RecordingResponse;
import com.ainclusive.iotsim.domain.recording.Recording;
import com.ainclusive.iotsim.domain.recording.RecordingService;
import com.ainclusive.iotsim.platform.capture.CaptureException;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** Unit test for {@link RecordingCaptureController} (live capture start/stop). */
class RecordingCaptureControllerTest {

    private RecordingService service;
    private RecordingCaptureController controller;

    @BeforeEach
    void setUp() {
        service = mock(RecordingService.class);
        controller = new RecordingCaptureController(service);
    }

    private static Recording sample(long version) {
        return new Recording("rec1", "p1", "ds1", 2, "SCAN_RECORD", "SCHEMA_AND_DATA", null, 0, Instant.now(), "local", version);
    }

    @Test
    void startReturns201WithLocationAndEtag() {
        given(service.startCapture("p1", "ds1", "local")).willReturn(sample(0));

        ResponseEntity<RecordingResponse> resp = controller.start("p1", "ds1");

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(resp.getHeaders().getLocation())
                .hasToString("/api/v1/projects/p1/recordings/rec1");
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().origin()).isEqualTo("SCAN_RECORD");
    }

    @Test
    void stopReturns200WithFinalizedRecording() {
        given(service.stopCapture("p1", "ds1")).willReturn(sample(1));

        ResponseEntity<RecordingResponse> resp = controller.stop("p1", "ds1");

        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"1\"");
        assertThat(resp.getBody()).isNotNull();
    }

    @Test
    void startPropagatesCaptureConflict() {
        given(service.startCapture("p1", "ds1", "local"))
                .willThrow(new CaptureException(
                        CaptureException.Kind.CONFLICT, "a capture is already running for this data source"));

        assertThatThrownBy(() -> controller.start("p1", "ds1"))
                .isInstanceOf(CaptureException.class);
    }
}
