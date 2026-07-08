package com.ainclusive.iotsim.persistence.activityevent;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.project.JooqProjectRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Append-only activity-event log against real Postgres (IS-083). */
@Testcontainers(disabledWithoutDocker = true)
class ActivityEventRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static DSLContext dsl;
    static ActivityEventRepository events;
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
        events = new JooqActivityEventRepository(dsl);
    }

    @Test
    void appendReturnsPersistedRowWithDetail() {
        ActivityEventRow row = events.append(
                projectId, "alice", "create", "data_source", "ds-1", "{\"name\":\"pump\"}");

        assertThat(row.id()).isPositive();
        assertThat(row.projectId()).isEqualTo(projectId);
        assertThat(row.actor()).isEqualTo("alice");
        assertThat(row.action()).isEqualTo("create");
        assertThat(row.objectType()).isEqualTo("data_source");
        assertThat(row.objectId()).isEqualTo("ds-1");
        assertThat(row.at()).isNotNull();
        assertThat(row.detailJson()).contains("\"name\"");
    }

    @Test
    void nullProjectIdAndObjectIdAndDetailAreAccepted() {
        ActivityEventRow row = events.append(null, "system", "role_change", "user", null, null);

        assertThat(row.projectId()).isNull();
        assertThat(row.objectId()).isNull();
        assertThat(row.detailJson()).isEqualTo("{}");
    }

    @Test
    void queryFiltersByProjectIdActorActionAndObjectType() {
        String project = new JooqProjectRepository(dsl).insert("Filter-project", null, "it").id();
        OffsetDateTime base = OffsetDateTime.parse("2026-06-01T00:00:00Z");
        events.append(project, "alice", "create", "data_source", "ds-A", null);
        events.append(project, "bob", "delete", "data_source", "ds-B", null);
        events.append(project, "alice", "create", "scenario", "sc-1", null);

        List<ActivityEventRow> byActor = events.query(
                ActivityEventQuery.builder().projectId(project).actor("alice").build());
        assertThat(byActor).extracting(ActivityEventRow::objectId)
                .containsExactlyInAnyOrder("ds-A", "sc-1");

        List<ActivityEventRow> byAction = events.query(
                ActivityEventQuery.builder().projectId(project).action("delete").build());
        assertThat(byAction).extracting(ActivityEventRow::objectId).containsExactly("ds-B");

        List<ActivityEventRow> byType = events.query(
                ActivityEventQuery.builder().projectId(project).objectType("scenario").build());
        assertThat(byType).extracting(ActivityEventRow::objectId).containsExactly("sc-1");
    }

    @Test
    void queryFiltersByTimeRange() {
        String project = new JooqProjectRepository(dsl).insert("Time-project", null, "it").id();
        OffsetDateTime base = OffsetDateTime.parse("2026-07-01T00:00:00Z");

        // Insert with explicit timestamps by using direct dsl to bypass the DB default
        dsl.insertInto(org.jooq.impl.DSL.table("activity_events"))
                .set(org.jooq.impl.DSL.field("project_id"), project)
                .set(org.jooq.impl.DSL.field("actor"), "alice")
                .set(org.jooq.impl.DSL.field("action"), "create")
                .set(org.jooq.impl.DSL.field("object_type"), "data_source")
                .set(org.jooq.impl.DSL.field("at", OffsetDateTime.class), base)
                .execute();
        dsl.insertInto(org.jooq.impl.DSL.table("activity_events"))
                .set(org.jooq.impl.DSL.field("project_id"), project)
                .set(org.jooq.impl.DSL.field("actor"), "bob")
                .set(org.jooq.impl.DSL.field("action"), "delete")
                .set(org.jooq.impl.DSL.field("object_type"), "data_source")
                .set(org.jooq.impl.DSL.field("at", OffsetDateTime.class), base.plusSeconds(20))
                .execute();

        // from inclusive, to exclusive: only +0s falls in [base, base+10s)
        List<ActivityEventRow> rows = events.query(ActivityEventQuery.builder()
                .projectId(project)
                .from(base)
                .to(base.plusSeconds(10))
                .build());

        assertThat(rows).extracting(ActivityEventRow::actor).containsExactly("alice");
    }

    @Test
    void queryPaginatesNewestFirstWithKeysetCursor() {
        String project = new JooqProjectRepository(dsl).insert("Page-project", null, "it").id();
        OffsetDateTime base = OffsetDateTime.parse("2026-08-01T00:00:00Z");

        dsl.insertInto(org.jooq.impl.DSL.table("activity_events"))
                .set(org.jooq.impl.DSL.field("project_id"), project)
                .set(org.jooq.impl.DSL.field("actor"), "alice")
                .set(org.jooq.impl.DSL.field("action"), "E1")
                .set(org.jooq.impl.DSL.field("object_type"), "data_source")
                .set(org.jooq.impl.DSL.field("at", OffsetDateTime.class), base)
                .execute();
        dsl.insertInto(org.jooq.impl.DSL.table("activity_events"))
                .set(org.jooq.impl.DSL.field("project_id"), project)
                .set(org.jooq.impl.DSL.field("actor"), "alice")
                .set(org.jooq.impl.DSL.field("action"), "E2")
                .set(org.jooq.impl.DSL.field("object_type"), "data_source")
                .set(org.jooq.impl.DSL.field("at", OffsetDateTime.class), base.plusSeconds(10))
                .execute();
        dsl.insertInto(org.jooq.impl.DSL.table("activity_events"))
                .set(org.jooq.impl.DSL.field("project_id"), project)
                .set(org.jooq.impl.DSL.field("actor"), "alice")
                .set(org.jooq.impl.DSL.field("action"), "E3")
                .set(org.jooq.impl.DSL.field("object_type"), "data_source")
                .set(org.jooq.impl.DSL.field("at", OffsetDateTime.class), base.plusSeconds(20))
                .execute();

        List<ActivityEventRow> page1 = events.query(
                ActivityEventQuery.builder().projectId(project).limit(2).build());
        assertThat(page1).extracting(ActivityEventRow::action).containsExactly("E3", "E2");

        ActivityEventRow last = page1.get(1);
        List<ActivityEventRow> page2 = events.query(ActivityEventQuery.builder()
                .projectId(project)
                .limit(2)
                .before(last.at(), last.id())
                .build());
        assertThat(page2).extracting(ActivityEventRow::action).containsExactly("E1");
    }

    @Test
    void queryWithNoFiltersReturnsAllEvents() {
        // admin endpoint: no projectId filter
        String project = new JooqProjectRepository(dsl).insert("Admin-query", null, "it").id();
        events.append(project, "alice", "create", "data_source", "ds-X", null);

        List<ActivityEventRow> all = events.query(ActivityEventQuery.builder().build());
        assertThat(all).isNotEmpty();
    }
}
