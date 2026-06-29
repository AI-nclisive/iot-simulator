package com.ainclusive.iotsim.app.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.clients.ClientObservationController;
import com.ainclusive.iotsim.api.stream.ClientStreamController;
import com.ainclusive.iotsim.api.stream.LiveStreamRegistry;
import com.ainclusive.iotsim.domain.clientobservation.ClientObservationService;
import com.ainclusive.iotsim.persistence.clientconnection.ClientConnectionRepository;
import com.ainclusive.iotsim.persistence.clientconnection.ClientConnectionRow;
import java.time.OffsetDateTime;
import java.util.ArrayList;
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

@WebMvcTest(controllers = {ClientObservationController.class, ClientStreamController.class},
        excludeAutoConfiguration = SecurityAutoConfiguration.class)
@Import(ClientObservationEndToEndTest.TestBeans.class)
class ClientObservationEndToEndTest {

    /** In-memory client-connection log seeded by the tests. */
    static final class StubClientConnections implements ClientConnectionRepository {
        final List<ClientConnectionRow> rows = new ArrayList<>();

        void seed(ClientConnectionRow row) {
            rows.add(row);
        }

        @Override
        public ClientConnectionRow open(String dataSourceId, String clientId, OffsetDateTime at) {
            ClientConnectionRow row = new ClientConnectionRow("id", dataSourceId, clientId, at, null, "{}");
            rows.add(row);
            return row;
        }

        @Override
        public int close(String dataSourceId, String clientId, OffsetDateTime at) {
            return 0;
        }

        @Override
        public List<ClientConnectionRow> findCurrent(String dataSourceId) {
            return rows.stream()
                    .filter(r -> r.dataSourceId().equals(dataSourceId) && r.disconnectedAt() == null)
                    .toList();
        }

        @Override
        public List<ClientConnectionRow> findByDataSource(String dataSourceId) {
            return rows.stream().filter(r -> r.dataSourceId().equals(dataSourceId)).toList();
        }
    }

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
        StubClientConnections clientConnections() {
            return new StubClientConnections();
        }

        @Bean
        ClientObservationService clientObservationService(StubClientConnections repo) {
            return new ClientObservationService(repo);
        }
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    LiveStreamRegistry registry;

    @Autowired
    StubClientConnections repo;

    @Test
    void clientsEndpointReturnsConnectedAndHistory() throws Exception {
        repo.seed(new ClientConnectionRow(
                "1", "d1", "c-open", OffsetDateTime.parse("2026-02-01T08:00:00Z"), null, "{}"));
        repo.seed(new ClientConnectionRow(
                "2", "d1", "c-closed", OffsetDateTime.parse("2026-02-01T07:00:00Z"),
                OffsetDateTime.parse("2026-02-01T07:30:00Z"), "{}"));

        MvcResult result = mvc.perform(get("/api/v1/data-sources/d1/clients"))
                .andExpect(status().isOk())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("\"clientId\":\"c-open\"");
        assertThat(body).contains("\"connected\":true");
        assertThat(body).contains("\"clientId\":\"c-closed\"");
        assertThat(body).contains("\"connected\":false");
    }

    @Test
    void clientsStreamStartsWithASnapshotOfCurrentClients() throws Exception {
        repo.seed(new ClientConnectionRow(
                "1", "d2", "c-open", OffsetDateTime.parse("2026-02-01T08:00:00Z"), null, "{}"));

        MvcResult result = mvc.perform(get("/api/v1/data-sources/d2/stream/clients"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        registry.close(); // completes the emitter so the async body finalizes
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk());

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:clients-snapshot");
        assertThat(body).contains("c-open");
    }
}
