package com.ainclusive.iotsim.app.runtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.ainclusive.iotsim.persistence.clientconnection.ClientConnectionRepository;
import com.ainclusive.iotsim.persistence.clientconnection.ClientConnectionRow;
import com.ainclusive.iotsim.platform.runtime.ClientActivityEvent;
import com.ainclusive.iotsim.platform.runtime.ClientActivityEvent.Kind;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PersistingClientActivityListenerTest {

    /** Records repository calls; runs synchronously via a direct executor. */
    static final class RecordingRepo implements ClientConnectionRepository {
        record Open(String dataSourceId, String clientId, OffsetDateTime at) {}

        record Close(String dataSourceId, String clientId, OffsetDateTime at) {}

        final List<Open> opens = new ArrayList<>();
        final List<Close> closes = new ArrayList<>();

        @Override
        public ClientConnectionRow open(String dataSourceId, String clientId, OffsetDateTime at) {
            opens.add(new Open(dataSourceId, clientId, at));
            return new ClientConnectionRow("id", dataSourceId, clientId, at, null, "{}");
        }

        @Override
        public int close(String dataSourceId, String clientId, OffsetDateTime at) {
            closes.add(new Close(dataSourceId, clientId, at));
            return 1;
        }

        @Override
        public List<ClientConnectionRow> findCurrent(String dataSourceId) {
            return List.of();
        }

        @Override
        public List<ClientConnectionRow> findByDataSource(String dataSourceId) {
            return List.of();
        }
    }

    private final RecordingRepo repo = new RecordingRepo();
    private final PersistingClientActivityListener listener =
            new PersistingClientActivityListener(repo, Runnable::run);

    @Test
    void connectedOpensAConnectionAtTheEventTime() {
        Instant at = Instant.parse("2026-02-01T08:00:00Z");

        listener.onClientActivity(new ClientActivityEvent("ds-1", Kind.CONNECTED, "c-1", at));

        assertThat(repo.opens).singleElement().satisfies(o -> {
            assertThat(o.dataSourceId()).isEqualTo("ds-1");
            assertThat(o.clientId()).isEqualTo("c-1");
            assertThat(o.at()).isEqualTo(at.atOffset(ZoneOffset.UTC));
        });
        assertThat(repo.closes).isEmpty();
    }

    @Test
    void disconnectedClosesAConnectionAtTheEventTime() {
        Instant at = Instant.parse("2026-02-01T09:00:00Z");

        listener.onClientActivity(new ClientActivityEvent("ds-1", Kind.DISCONNECTED, "c-1", at));

        assertThat(repo.closes).singleElement().satisfies(c -> {
            assertThat(c.dataSourceId()).isEqualTo("ds-1");
            assertThat(c.clientId()).isEqualTo("c-1");
            assertThat(c.at()).isEqualTo(at.atOffset(ZoneOffset.UTC));
        });
        assertThat(repo.opens).isEmpty();
    }

    @Test
    void subscriptionIsNotAConnectionLifecycleChange() {
        listener.onClientActivity(new ClientActivityEvent("ds-1", Kind.SUBSCRIPTION, "c-1", Instant.EPOCH));

        assertThat(repo.opens).isEmpty();
        assertThat(repo.closes).isEmpty();
    }

    @Test
    void survivesARepositoryThatThrows() {
        ClientConnectionRepository throwing = new ClientConnectionRepository() {
            @Override
            public ClientConnectionRow open(String dataSourceId, String clientId, OffsetDateTime at) {
                throw new RuntimeException("db boom");
            }

            @Override
            public int close(String dataSourceId, String clientId, OffsetDateTime at) {
                return 0;
            }

            @Override
            public List<ClientConnectionRow> findCurrent(String dataSourceId) {
                return List.of();
            }

            @Override
            public List<ClientConnectionRow> findByDataSource(String dataSourceId) {
                return List.of();
            }
        };
        PersistingClientActivityListener resilient =
                new PersistingClientActivityListener(throwing, Runnable::run);

        assertThatCode(() -> resilient.onClientActivity(
                new ClientActivityEvent("ds-1", Kind.CONNECTED, "c-1", Instant.EPOCH)))
                .doesNotThrowAnyException();
    }
}
