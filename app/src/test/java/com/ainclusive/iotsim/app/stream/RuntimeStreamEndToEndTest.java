package com.ainclusive.iotsim.app.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.stream.LiveStreamRegistry;
import com.ainclusive.iotsim.api.stream.ProjectSources;
import com.ainclusive.iotsim.api.stream.RuntimeStateSnapshot;
import com.ainclusive.iotsim.api.stream.RuntimeStreamController;
import com.ainclusive.iotsim.api.stream.StreamKey;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = RuntimeStreamController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import(RuntimeStreamEndToEndTest.TestBeans.class)
class RuntimeStreamEndToEndTest {

    static class TestBeans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        LiveStreamRegistry liveStreamRegistry(ObjectMapper json) {
            return new LiveStreamRegistry(json, 256, 256, Runnable::run);
        }

        @Bean
        ProjectSources projectSources() {
            // Stub: return empty for this test (RuntimeStateSnapshot calls it on-connect,
            // but the test doesn't exercise that path — it only tests event publishing).
            return projectId -> List.of();
        }

        @Bean
        RuntimeController runtimeController() {
            // Stub: only RuntimeStateSnapshot uses this (for state() calls on-connect).
            return new RuntimeController() {
                @Override
                public String start(String dataSourceId, RuntimeStartSpec spec) {
                    return "RUNNING";
                }

                @Override
                public String stop(String dataSourceId) {
                    return "STOPPED";
                }

                @Override
                public String state(String dataSourceId) {
                    return "IDLE";
                }

                @Override
                public long applyValues(String dataSourceId, List values) {
                    return 0;
                }
            };
        }

        @Bean
        RuntimeStateSnapshot runtimeStateSnapshot(RuntimeController runtimeController, ProjectSources projectSources) {
            return new RuntimeStateSnapshot(runtimeController, projectSources);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    LiveStreamRegistry registry;

    @Test
    void streamsAPublishedRuntimeEventAsFramedSse() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/projects/p1/stream/runtime"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        registry.publish(StreamKey.runtime("p1"), "SOURCE_START",
                Map.of("dataSourceId", "d1"), Instant.EPOCH);

        // Complete the emitter so the async response body is finalized, then dispatch.
        registry.close();
        mvc.perform(asyncDispatch(result))
                .andExpect(status().isOk());

        String body = result.getResponse().getContentAsString();
        // Spring emits: "event:SOURCE_START\ndata:{...}\nid:0\n\n"
        // Assert content-type is SSE (may include charset; Spring 7 omits charset for text/event-stream)
        assertThat(result.getResponse().getContentType())
                .as("Content-Type must be text/event-stream")
                .contains("text/event-stream");
        // Assert SSE framing: event name line
        assertThat(body)
                .as("Body must contain event:SOURCE_START framing line")
                .contains("event:SOURCE_START");
        // Assert SSE framing: event id line (seq starts at 0 for first published event)
        assertThat(body)
                .as("Body must contain id:0 framing line")
                .contains("id:0");
        // Assert JSON payload in data line
        assertThat(body)
                .as("Body must contain the published JSON payload")
                .contains("\"dataSourceId\":\"d1\"");
    }
}
