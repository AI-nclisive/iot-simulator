package com.ainclusive.iotsim.persistence.runtimeevent;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.project.JooqProjectRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Append-only runtime-event log against real Postgres (backend-specs/04, IS-049). */
@Testcontainers(disabledWithoutDocker = true)
class RuntimeEventRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static DSLContext dsl;
    static RuntimeEventRepository events;
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
        events = new JooqRuntimeEventRepository(dsl);
    }

    @Test
    void appendReturnsPersistedRowWithPayload() {
        OffsetDateTime at = OffsetDateTime.parse("2026-02-01T08:00:00Z");
        RuntimeEventRow row = events.append(
                projectId, "src-1", "run-1", "SOURCE_START", at, "{\"port\":4840}");

        assertThat(row.id()).isPositive();
        assertThat(row.projectId()).isEqualTo(projectId);
        assertThat(row.dataSourceId()).isEqualTo("src-1");
        assertThat(row.runId()).isEqualTo("run-1");
        assertThat(row.type()).isEqualTo("SOURCE_START");
        assertThat(row.at()).isEqualTo(at);
        assertThat(row.payloadJson()).contains("\"port\": 4840");

        assertThat(events.findById(row.id())).contains(row);
    }

    @Test
    void nullSourceRunAndPayloadAreAccepted() {
        RuntimeEventRow row = events.append(
                projectId, null, null, "ERROR", null, null);

        assertThat(row.dataSourceId()).isNull();
        assertThat(row.runId()).isNull();
        assertThat(row.at()).isNotNull(); // DB default now()
        assertThat(row.payloadJson()).isEqualTo("{}");
    }

    @Test
    void findByProjectReturnsNewestFirst() {
        String project = new JooqProjectRepository(dsl).insert("Ordering", null, "it").id();
        OffsetDateTime base = OffsetDateTime.parse("2026-03-01T00:00:00Z");
        events.append(project, null, null, "SOURCE_START", base, null);
        events.append(project, null, null, "FAULT_STATE_CHANGE", base.plusSeconds(10), null);
        events.append(project, null, null, "SOURCE_STOP", base.plusSeconds(20), null);

        List<RuntimeEventRow> rows = events.findByProject(project);

        assertThat(rows).extracting(RuntimeEventRow::type)
                .containsExactly("SOURCE_STOP", "FAULT_STATE_CHANGE", "SOURCE_START");
    }

    @Test
    void findByRunAndDataSourceFilter() {
        String project = new JooqProjectRepository(dsl).insert("Filtering", null, "it").id();
        OffsetDateTime at = OffsetDateTime.now(ZoneOffset.UTC);
        events.append(project, "pump", "runA", "SOURCE_START", at, null);
        events.append(project, "valve", "runB", "SOURCE_START", at, null);

        assertThat(events.findByRun("runA")).singleElement()
                .extracting(RuntimeEventRow::dataSourceId).isEqualTo("pump");
        assertThat(events.findByDataSource("valve")).singleElement()
                .extracting(RuntimeEventRow::runId).isEqualTo("runB");
    }
}
