package com.ainclusive.iotsim.domain.clientobservation;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.clientconnection.ClientConnectionRepository;
import com.ainclusive.iotsim.persistence.clientconnection.ClientConnectionRow;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ClientObservationServiceTest {

    /** Returns canned current/history rows; write methods are unused here. */
    static final class StubRepo implements ClientConnectionRepository {
        List<ClientConnectionRow> current = List.of();
        List<ClientConnectionRow> history = List.of();

        @Override
        public ClientConnectionRow open(String dataSourceId, String clientId, OffsetDateTime at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int close(String dataSourceId, String clientId, OffsetDateTime at) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ClientConnectionRow> findCurrent(String dataSourceId) {
            return current;
        }

        @Override
        public List<ClientConnectionRow> findByDataSource(String dataSourceId) {
            return history;
        }
    }

    @Test
    void currentMapsOpenRowsToConnectedViews() {
        StubRepo repo = new StubRepo();
        repo.current = List.of(new ClientConnectionRow(
                "id-1", "ds-1", "c-1", OffsetDateTime.parse("2026-02-01T08:00:00Z"), null, "{}"));
        ClientObservationService service = new ClientObservationService(repo);

        assertThat(service.current("ds-1")).singleElement().satisfies(v -> {
            assertThat(v.clientId()).isEqualTo("c-1");
            assertThat(v.connectedAt()).isEqualTo(Instant.parse("2026-02-01T08:00:00Z"));
            assertThat(v.disconnectedAt()).isNull();
            assertThat(v.connected()).isTrue();
        });
    }

    @Test
    void historyMarksClosedRowsAsDisconnected() {
        StubRepo repo = new StubRepo();
        repo.history = List.of(new ClientConnectionRow(
                "id-1", "ds-1", "c-1",
                OffsetDateTime.parse("2026-02-01T08:00:00Z"),
                OffsetDateTime.parse("2026-02-01T09:00:00Z"), "{}"));
        ClientObservationService service = new ClientObservationService(repo);

        assertThat(service.history("ds-1")).singleElement().satisfies(v -> {
            assertThat(v.clientId()).isEqualTo("c-1");
            assertThat(v.disconnectedAt()).isEqualTo(Instant.parse("2026-02-01T09:00:00Z"));
            assertThat(v.connected()).isFalse();
        });
    }
}
