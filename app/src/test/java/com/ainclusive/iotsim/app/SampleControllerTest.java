package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ainclusive.iotsim.api.sample.SampleController;
import com.ainclusive.iotsim.api.sample.SampleController.CreateSampleRequest;
import com.ainclusive.iotsim.api.sample.SampleController.SampleResponse;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.sample.Sample;
import com.ainclusive.iotsim.domain.sample.SampleService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** Unit test for {@link SampleController}. */
class SampleControllerTest {

    private static final String PROJECT = "proj-1";

    private SampleService service;
    private SampleController controller;

    @BeforeEach
    void setUp() {
        service = mock(SampleService.class);
        controller = new SampleController(service);
    }

    private static Sample sample() {
        return new Sample("smp-1", PROJECT, null, "Baseline", "{}", List.of("env"), Instant.now(), "local", 0);
    }

    @Test
    void createReturns201WithEtag() {
        given(service.create(any(), any(), any(), any(), any(), any())).willReturn(sample());
        ResponseEntity<SampleResponse> resp =
                controller.create(PROJECT, new CreateSampleRequest("Baseline", null, "{}", List.of("env")));
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().name()).isEqualTo("Baseline");
    }

    @Test
    void createWithBlankNameThrowsBadRequest() {
        assertThatThrownBy(() -> controller.create(
                PROJECT, new CreateSampleRequest(" ", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void listReturnsSamples() {
        given(service.list(PROJECT)).willReturn(List.of(sample()));
        List<SampleResponse> result = controller.list(PROJECT);
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("smp-1");
    }

    @Test
    void getMissingSample404() {
        given(service.get(PROJECT, "missing")).willThrow(new ResourceNotFoundException("Sample", "missing"));
        assertThatThrownBy(() -> controller.get(PROJECT, "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteReturns204() {
        assertThat(controller.delete(PROJECT, "smp-1").getStatusCode().value()).isEqualTo(204);
    }
}
