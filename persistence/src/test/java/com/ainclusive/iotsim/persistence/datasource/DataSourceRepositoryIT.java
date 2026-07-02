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
        DataSourceRow row = dataSources.insert(projectId, "Pump", "OPC_UA", "SCAN",
                4840, "opc.tcp://plc:4840", "{}", "local");
        assertThat(row.simulatorPort()).isEqualTo(4840);
        assertThat(row.realDeviceEndpoint()).isEqualTo("opc.tcp://plc:4840");

        DataSourceRow synthetic = dataSources.insert(projectId, "Sim", "OPC_UA", "SYNTHETIC",
                4841, null, "{}", "local");
        assertThat(synthetic.simulatorPort()).isEqualTo(4841);
        assertThat(synthetic.realDeviceEndpoint()).isNull();

        assertThat(row.id()).isNotBlank();
        assertThat(row.enabled()).isFalse();
        assertThat(row.version()).isZero();

        assertThat(dataSources.findByProject(projectId)).extracting(DataSourceRow::id).contains(row.id());

        Optional<DataSourceRow> updated = dataSources.update(
                row.id(), "Pump 2", 4842, "opc.tcp://plc2:4840", "{}", true, 0);
        assertThat(updated).isPresent();
        assertThat(updated.get().name()).isEqualTo("Pump 2");
        assertThat(updated.get().simulatorPort()).isEqualTo(4842);
        assertThat(updated.get().realDeviceEndpoint()).isEqualTo("opc.tcp://plc2:4840");
        assertThat(updated.get().enabled()).isTrue();
        assertThat(updated.get().version()).isEqualTo(1L);

        assertThat(dataSources.deleteById(row.id())).isTrue();
        assertThat(dataSources.findById(row.id())).isEmpty();

        dataSources.deleteById(synthetic.id());
    }

    @Test
    void insertPlainUrlEndpointRoundTrips() {
        // The UI sends the endpoint as a bare URL (not a JSON document). It must persist
        // into the jsonb column and read back unchanged. Regression for the create-500
        // (invalid input syntax for type json) reported against opc.tcp:// endpoints.
        String url = "opc.tcp://simulator.local:4840";

        DataSourceRow created = dataSources.insert(projectId, "OPC Source", "OPC_UA", "MANUAL",
                4840, url, null, "it");

        assertThat(created.realDeviceEndpoint()).isEqualTo(url);
        assertThat(dataSources.findById(created.id()))
                .get()
                .extracting(DataSourceRow::realDeviceEndpoint)
                .isEqualTo(url);

        dataSources.deleteById(created.id());
    }

    @Test
    void endpointWithJsonSpecialCharactersRoundTrips() {
        // A URL carrying characters that are significant inside a JSON string (quote,
        // backslash) must still round-trip — proving the value is escaped, not concatenated.
        String url = "opc.tcp://host/path?q=\"a\\b\"";

        DataSourceRow created = dataSources.insert(projectId, "Weird", "OPC_UA", "MANUAL",
                4840, url, null, "it");

        assertThat(dataSources.findById(created.id()))
                .get()
                .extracting(DataSourceRow::realDeviceEndpoint)
                .isEqualTo(url);

        dataSources.deleteById(created.id());
    }

    @Test
    void updateWithStaleVersionMatchesNothing() {
        DataSourceRow created = dataSources.insert(projectId, "Stale", "MODBUS_TCP", "SCAN",
                4840, null, null, "it");
        assertThat(dataSources.update(created.id(), "x", 4840, null, null, true, 999)).isEmpty();
        dataSources.deleteById(created.id());
    }

    @Test
    void duplicateCreatesNewRowWithSameProtocolAndEndpoint() {
        DataSourceRow source = dataSources.insert(
                projectId, "Boiler", "OPC_UA", "SCAN",
                4840, "opc.tcp://boiler1:4840", "{\"rate\":2000}", "it");

        Optional<DataSourceRow> copy = dataSources.duplicate(source.id(), "Boiler (copy)", "it");

        assertThat(copy).isPresent();
        assertThat(copy.get().id()).isNotEqualTo(source.id());
        assertThat(copy.get().name()).isEqualTo("Boiler (copy)");
        assertThat(copy.get().projectId()).isEqualTo(projectId);
        assertThat(copy.get().protocol()).isEqualTo("OPC_UA");
        assertThat(copy.get().basis()).isEqualTo("SCAN");
        assertThat(copy.get().simulatorPort()).isEqualTo(source.simulatorPort());
        assertThat(copy.get().realDeviceEndpoint()).isEqualTo(source.realDeviceEndpoint());
        assertThat(copy.get().runtimeConfig()).isEqualTo(source.runtimeConfig());
        assertThat(copy.get().enabled()).isFalse();
        assertThat(copy.get().version()).isZero();
        assertThat(copy.get().schemaId()).isNull();

        dataSources.deleteById(source.id());
        dataSources.deleteById(copy.get().id());
    }

    @Test
    void duplicateOnMissingSourceReturnsEmpty() {
        assertThat(dataSources.duplicate("no-such-id", "Copy", "it")).isEmpty();
    }

    @Test
    void findAllReturnsSourcesAcrossProjects() {
        DataSourceRow a = dataSources.insert(projectId, "A", "OPC_UA", "MANUAL", 4840, null, null, "it");
        DataSourceRow b = dataSources.insert(projectId, "B", "OPC_UA", "MANUAL", 4841, null, null, "it");

        List<DataSourceRow> all = dataSources.findAll();

        assertThat(all).extracting(DataSourceRow::id).contains(a.id(), b.id());

        dataSources.deleteById(a.id());
        dataSources.deleteById(b.id());
    }

    @Test
    void findByProjectPagedReturnsBatchNewestFirst() {
        DataSourceRow a = dataSources.insert(projectId, "Paged-A", "OPC_UA", "SCAN", 4840, null, null, "it");
        DataSourceRow b = dataSources.insert(projectId, "Paged-B", "OPC_UA", "SCAN", 4841, null, null, "it");
        DataSourceRow c = dataSources.insert(projectId, "Paged-C", "MODBUS_TCP", "SCAN", 4842, null, null, "it");

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
