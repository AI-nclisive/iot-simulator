package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ainclusive.iotsim.api.recording.RecordingController;
import com.ainclusive.iotsim.api.recording.RecordingController.CreateRecordingRequest;
import com.ainclusive.iotsim.api.recording.RecordingController.RecordingResponse;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.recording.Recording;
import com.ainclusive.iotsim.domain.recording.RecordingService;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** Unit test for {@link RecordingController}. */
class RecordingControllerTest {

    private RecordingService service;
    private RecordingController controller;

    @BeforeEach
    void setUp() {
        service = mock(RecordingService.class);
        controller = new RecordingController(service);
    }

    private static Recording sample() {
        return new Recording("rec1", "p1", "ds1", 1, "SCAN_RECORD", "SCHEMA_AND_DATA", null, 0, Instant.now(), "local", 0);
    }

    @Test
    void createReturns201WithEtag() {
        given(service.create(any(), any(), any(), any(), any())).willReturn(sample());
        ResponseEntity<RecordingResponse> resp =
                controller.create("p1", new CreateRecordingRequest("ds1", null, null));
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().origin()).isEqualTo("SCAN_RECORD");
    }

    @Test
    void createWithoutDataSourceIsRejected() {
        assertThatThrownBy(() -> controller.create("p1", new CreateRecordingRequest(" ", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getMissingPropagatesNotFound() {
        given(service.get("p1", "missing")).willThrow(new ResourceNotFoundException("Recording", "missing"));
        assertThatThrownBy(() -> controller.get("p1", "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
