package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.runtimeevent.RuntimeEventHistoryController;
import com.ainclusive.iotsim.domain.runtimeevent.RuntimeEventHistoryPage;
import com.ainclusive.iotsim.domain.runtimeevent.RuntimeEventHistoryRequest;
import com.ainclusive.iotsim.domain.runtimeevent.RuntimeEventHistoryService;
import com.ainclusive.iotsim.domain.runtimeevent.RuntimeEventView;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = RuntimeEventHistoryController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
class RuntimeEventHistoryEndToEndTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    RuntimeEventHistoryService service;

    @Test
    void returnsEventsWithNestedPayloadObjectAndNextCursor() throws Exception {
        given(service.history(any())).willReturn(new RuntimeEventHistoryPage(
                List.of(new RuntimeEventView(7, "ERROR", Instant.parse("2026-06-01T00:00:00Z"),
                        "pump", "runX", "{\"reason\":\"timeout\"}")),
                "CURSOR2"));

        MvcResult result = mvc.perform(get("/api/v1/projects/p1/runtime-events"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"id\":7");
        assertThat(body).contains("\"type\":\"ERROR\"");
        assertThat(body).contains("\"dataSourceId\":\"pump\"");
        assertThat(body).contains("\"runId\":\"runX\"");
        // payload renders as a nested JSON object, never a JSON-encoded string.
        assertThat(body).contains("\"payload\":{\"reason\":\"timeout\"}");
        assertThat(body).contains("\"nextCursor\":\"CURSOR2\"");
        assertThat(body).contains("2026-06-01T00:00:00Z");
    }

    @Test
    void bindsPathAndQueryParametersIntoTheRequest() throws Exception {
        given(service.history(any())).willReturn(new RuntimeEventHistoryPage(List.of(), null));

        mvc.perform(get("/api/v1/projects/p1/runtime-events")
                        .param("source", "pump")
                        .param("run", "runX")
                        .param("type", "ERROR")
                        .param("from", "2026-06-01T00:00:00Z")
                        .param("to", "2026-06-02T00:00:00Z")
                        .param("cursor", "C1")
                        .param("limit", "25"))
                .andExpect(status().isOk());

        ArgumentCaptor<RuntimeEventHistoryRequest> captor =
                ArgumentCaptor.forClass(RuntimeEventHistoryRequest.class);
        verify(service).history(captor.capture());
        RuntimeEventHistoryRequest req = captor.getValue();
        assertThat(req.projectId()).isEqualTo("p1");
        assertThat(req.dataSourceId()).isEqualTo("pump");
        assertThat(req.runId()).isEqualTo("runX");
        assertThat(req.type()).isEqualTo("ERROR");
        assertThat(req.from()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(req.to()).isEqualTo(Instant.parse("2026-06-02T00:00:00Z"));
        assertThat(req.cursor()).isEqualTo("C1");
        assertThat(req.limit()).isEqualTo(25);
    }

    @Test
    void emptyHistoryReturnsEmptyEventsAndNullCursor() throws Exception {
        given(service.history(any())).willReturn(new RuntimeEventHistoryPage(List.of(), null));

        MvcResult result = mvc.perform(get("/api/v1/projects/empty/runtime-events"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"events\":[]");
        assertThat(body).contains("\"nextCursor\":null");
    }

    @Test
    void malformedCursorMapsToBadRequest() throws Exception {
        given(service.history(any()))
                .willThrow(new IllegalArgumentException("invalid runtime-event cursor"));

        mvc.perform(get("/api/v1/projects/p1/runtime-events").param("cursor", "bad"))
                .andExpect(status().isBadRequest());
    }
}
