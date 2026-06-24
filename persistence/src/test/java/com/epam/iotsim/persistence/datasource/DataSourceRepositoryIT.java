package com.epam.iotsim.persistence.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import com.epam.iotsim.persistence.project.JooqProjectRepository;
import com.epam.iotsim.persistence.project.ProjectRow;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises the jOOQ data-source repository (incl. JSONB) against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
class DataSourceRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static DataSourceRepository dataSources;
    static String projectId;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DSLContext dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ProjectRow project = new JooqProjectRepository(dsl).insert("Plant", null, "it");
        projectId = project.id();
        dataSources = new JooqDataSourceRepository(dsl);
    }

    @Test
    void insertFindUpdateDeleteWithJsonb() {
        DataSourceRow created = dataSources.insert(
                projectId, "Pump", "OPC_UA", "MANUAL",
                "{\"host\":\"plc1\"}", "{\"rate\":1000}", "it");
        assertThat(created.id()).isNotBlank();
        assertThat(created.enabled()).isFalse();
        assertThat(created.endpoint()).contains("plc1");
        assertThat(created.version()).isZero();

        assertThat(dataSources.findByProject(projectId)).extracting(DataSourceRow::id).contains(created.id());

        Optional<DataSourceRow> updated = dataSources.update(
                created.id(), "Pump 2", "{\"host\":\"plc2\"}", "{}", true, 0);
        assertThat(updated).isPresent();
        assertThat(updated.get().name()).isEqualTo("Pump 2");
        assertThat(updated.get().endpoint()).contains("plc2");
        assertThat(updated.get().enabled()).isTrue();
        assertThat(updated.get().version()).isEqualTo(1L);

        assertThat(dataSources.deleteById(created.id())).isTrue();
        assertThat(dataSources.findById(created.id())).isEmpty();
    }

    @Test
    void updateWithStaleVersionMatchesNothing() {
        DataSourceRow created = dataSources.insert(projectId, "Stale", "MODBUS_TCP", "SCAN", null, null, "it");
        assertThat(dataSources.update(created.id(), "x", null, null, true, 999)).isEmpty();
    }
}
