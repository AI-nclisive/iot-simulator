package com.ainclusive.iotsim.persistence.clientconnection;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.datasource.JooqDataSourceRepository;
import com.ainclusive.iotsim.persistence.project.JooqProjectRepository;
import java.time.OffsetDateTime;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Client-connection log against real Postgres (backend-specs/04, IS-052). */
@Testcontainers(disabledWithoutDocker = true)
class ClientConnectionRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static DSLContext dsl;
    static JooqDataSourceRepository dataSources;
    static ClientConnectionRepository clients;
    static String projectId;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        projectId = new JooqProjectRepository(dsl).insert("Plant", null, "it").id();
        dataSources = new JooqDataSourceRepository(dsl);
        clients = new JooqClientConnectionRepository(dsl);
    }

    /** Fresh source per test so connection sets/ordering don't bleed across tests. */
    private static String newSource(String name) {
        return dataSources.insert(projectId, name, "OPC_UA", "MANUAL", null, null, "it").id();
    }

    @Test
    void openReturnsPersistedOpenConnection() {
        String source = newSource("open");
        OffsetDateTime at = OffsetDateTime.parse("2026-02-01T08:00:00Z");

        ClientConnectionRow row = clients.open(source, "client-A", at);

        assertThat(row.id()).isNotBlank();
        assertThat(row.dataSourceId()).isEqualTo(source);
        assertThat(row.clientId()).isEqualTo("client-A");
        assertThat(row.connectedAt()).isEqualTo(at);
        assertThat(row.disconnectedAt()).isNull();
    }

    @Test
    void findCurrentReturnsOnlyOpenConnections() {
        String source = newSource("current");
        clients.open(source, "still-open", OffsetDateTime.parse("2026-02-02T08:00:00Z"));
        clients.open(source, "gone", OffsetDateTime.parse("2026-02-02T08:01:00Z"));
        clients.close(source, "gone", OffsetDateTime.parse("2026-02-02T09:00:00Z"));

        assertThat(clients.findCurrent(source))
                .extracting(ClientConnectionRow::clientId)
                .containsExactly("still-open");
    }

    @Test
    void closeSetsDisconnectedAtOnLatestOpenConnection() {
        String source = newSource("reconnect");
        OffsetDateTime first = OffsetDateTime.parse("2026-03-01T08:00:00Z");
        OffsetDateTime second = OffsetDateTime.parse("2026-03-01T09:00:00Z");
        clients.open(source, "reconnector", first);
        clients.open(source, "reconnector", second);

        int closed = clients.close(source, "reconnector", OffsetDateTime.parse("2026-03-01T10:00:00Z"));

        assertThat(closed).isEqualTo(1);
        // The later connection was closed; the earlier one is still open.
        assertThat(clients.findCurrent(source))
                .singleElement()
                .satisfies(r -> assertThat(r.connectedAt()).isEqualTo(first));
    }

    @Test
    void closeReturnsZeroWhenNoOpenConnection() {
        String source = newSource("noop-close");

        assertThat(clients.close(source, "never-connected", OffsetDateTime.parse("2026-03-02T10:00:00Z")))
                .isZero();
    }

    @Test
    void findByDataSourceReturnsFullLogNewestFirst() {
        String source = newSource("history");
        clients.open(source, "oldest", OffsetDateTime.parse("2026-04-01T08:00:00Z"));
        clients.open(source, "middle", OffsetDateTime.parse("2026-04-01T09:00:00Z"));
        clients.close(source, "middle", OffsetDateTime.parse("2026-04-01T09:30:00Z"));
        clients.open(source, "newest", OffsetDateTime.parse("2026-04-01T10:00:00Z"));

        assertThat(clients.findByDataSource(source))
                .extracting(ClientConnectionRow::clientId)
                .containsExactly("newest", "middle", "oldest");
    }
}
