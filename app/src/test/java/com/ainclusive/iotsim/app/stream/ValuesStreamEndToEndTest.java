package com.ainclusive.iotsim.app.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.stream.LiveStreamRegistry;
import com.ainclusive.iotsim.api.stream.LiveValueStore;
import com.ainclusive.iotsim.api.stream.LiveValuesHub;
import com.ainclusive.iotsim.api.stream.ValuesStreamController;
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
import tools.jackson.databind.ObjectMapper;

@WebMvcTest(controllers = ValuesStreamController.class,
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import(ValuesStreamEndToEndTest.TestBeans.class)
class ValuesStreamEndToEndTest {

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
        LiveValueStore liveValueStore() {
            return new LiveValueStore();
        }

        @Bean
        LiveValuesHub liveValuesHub(LiveStreamRegistry registry, LiveValueStore store) {
            return new LiveValuesHub(registry, store); // test ctor: no scheduler
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    LiveStreamRegistry registry;

    @Autowired
    LiveValueStore store;

    @Autowired
    LiveValuesHub hub;

    @Test
    void streamsSnapshotThenDelta() throws Exception {
        store.record("d1", List.of(NeutralValue.good("n1", Instant.EPOCH, 1))); // seeds snapshot

        MvcResult result = mvc.perform(get("/api/v1/data-sources/d1/stream/values"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        store.record("d1", List.of(NeutralValue.good("n1", Instant.EPOCH, 2))); // change
        hub.flushTick(); // publish the "values" delta

        registry.close(); // completes the emitter so the async body finalizes
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:values-snapshot");
        assertThat(body).contains("event:values");
        assertThat(body).contains("\"nodeId\":\"n1\"");
    }
}
