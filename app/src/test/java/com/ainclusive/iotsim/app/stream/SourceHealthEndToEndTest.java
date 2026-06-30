package com.ainclusive.iotsim.app.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.health.HealthController;
import com.ainclusive.iotsim.platform.runtime.HealthOrigin;
import com.ainclusive.iotsim.platform.runtime.RuntimeController;
import com.ainclusive.iotsim.platform.runtime.RuntimeStartSpec;
import com.ainclusive.iotsim.platform.runtime.SourceError;
import com.ainclusive.iotsim.platform.runtime.SourceHealth;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@WebMvcTest(controllers = HealthController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import(SourceHealthEndToEndTest.TestBeans.class)
class SourceHealthEndToEndTest {

    static class TestBeans {
        @Bean
        RuntimeController runtimeController() {
            return new RuntimeController() {
                public String start(String id, RuntimeStartSpec s) { return "RUNNING"; }
                public String stop(String id) { return "STOPPED"; }
                public String state(String id) { return "STALE"; }
                public long applyValues(String id, List<NeutralValue> v) { return 0; }
                public SourceHealth health(String id) {
                    if (id.equals("d1")) {
                        return new SourceHealth("STALE", new SourceError(
                                HealthOrigin.SIMULATOR, "no health response in 3 probes",
                                Instant.parse("2026-06-30T12:00:00Z")));
                    }
                    return new SourceHealth("STOPPED", null);
                }
            };
        }
    }

    @Autowired
    MockMvc mvc;

    @Test
    void healthEndpointReturnsStateAndError() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/data-sources/d1/health"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"state\":\"STALE\"");
        assertThat(body).contains("\"origin\":\"SIMULATOR\"");
        assertThat(body).contains("no health response in 3 probes");
    }

    @Test
    void unknownSourceReturnsStoppedWithNoError() throws Exception {
        MvcResult result = mvc.perform(get("/api/v1/data-sources/unknown/health"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"state\":\"STOPPED\"");
        assertThat(body).contains("\"lastError\":null");
    }
}
