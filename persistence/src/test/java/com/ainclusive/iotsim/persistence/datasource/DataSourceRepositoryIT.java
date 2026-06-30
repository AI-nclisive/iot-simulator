package com.ainclusive.iotsim.persistence.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.project.JooqProjectRepository;
import com.ainclusive.iotsim.persistence.project.ProjectRow;
import java.util.List;
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

    @Test
    void duplicateCreatesNewRowWithSameProtocolAndEndpoint() {
        DataSourceRow source = dataSources.insert(
                projectId, "Boiler", "OPC_UA", "SCAN",
                "{\"host\":\"boiler1\"}", "{\"rate\":2000}", "it");

        Optional<DataSourceRow> copy = dataSources.duplicate(source.id(), "Boiler (copy)", "it");

        assertThat(copy).isPresent();
        assertThat(copy.get().id()).isNotEqualTo(source.id());
        assertThat(copy.get().name()).isEqualTo("Boiler (copy)");
        assertThat(copy.get().projectId()).isEqualTo(projectId);
        assertThat(copy.get().protocol()).isEqualTo("OPC_UA");
        assertThat(copy.get().basis()).isEqualTo("SCAN");
        assertThat(copy.get().endpoint()).isEqualTo(source.endpoint());
        assertThat(copy.get().runtimeConfig()).isEqualTo(source.runtimeConfig());
        assertThat(copy.get().enabled()).isFalse();
        assertThat(copy.get().version()).isZero();
        assertThat(copy.get().schemaId()).isNull();
    }

    @Test
    void duplicateOnMissingSourceReturnsEmpty() {
        assertThat(dataSources.duplicate("no-such-id", "Copy", "it")).isEmpty();
    }

    @Test
    void findByProjectPagedReturnsBatchNewestFirst() {
        DataSourceRow a = dataSources.insert(projectId, "Paged-A", "OPC_UA", "SCAN", null, null, "it");
        DataSourceRow b = dataSources.insert(projectId, "Paged-B", "OPC_UA", "SCAN", null, null, "it");
        DataSourceRow c = dataSources.insert(projectId, "Paged-C", "MODBUS_TCP", "SCAN", null, null, "it");

        List<DataSourceRow> page1 = dataSources.findByProjectPaged(projectId, null, null, null, 2);
        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).id()).isEqualTo(c.id());
        assertThat(page1.get(1).id()).isEqualTo(b.id());

        DataSourceRow last = page1.get(page1.size() - 1);
        List<DataSourceRow> page2 = dataSources.findByProjectPaged(projectId, null, last.createdAt(), last.id(), 2);
        assertThat(page2).extracting(DataSourceRow::id).contains(a.id());
        assertThat(page2).extracting(DataSourceRow::id).doesNotContain(b.id(), c.id());

        // protocol filter
        List<DataSourceRow> opcOnly = dataSources.findByProjectPaged(projectId, "OPC_UA", null, null, 10);
        assertThat(opcOnly).extracting(DataSourceRow::id).contains(a.id(), b.id());
        assertThat(opcOnly).extracting(DataSourceRow::id).doesNotContain(c.id());

        dataSources.deleteById(a.id());
        dataSources.deleteById(b.id());
        dataSources.deleteById(c.id());
    }
}
