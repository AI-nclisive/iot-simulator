package com.ainclusive.iotsim.persistence.run;

import static java.util.Comparator.comparing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.persistence.datasource.JooqDataSourceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import com.ainclusive.iotsim.persistence.evidence.JooqEvidenceRepository;
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

/** Runs and their run_sources join against real Postgres (backend-specs/04, IS-050). */
@Testcontainers(disabledWithoutDocker = true)
class RunRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static DSLContext dsl;
    static JooqProjectRepository projects;
    static JooqDataSourceRepository sources;
    static RunRepository runs;
    static JooqEvidenceRepository evidence;
    static String projectId;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        projects = new JooqProjectRepository(dsl);
        sources = new JooqDataSourceRepository(dsl);
        runs = new JooqRunRepository(dsl);
        evidence = new JooqEvidenceRepository(dsl);
        projectId = projects.insert("Plant", null, "it").id();
    }

    private String source(String name) {
        return sources.insert(projectId, name, "OPC_UA", "MANUAL", null, null, "it").id();
    }

    @Test
    void createPersistsRunWithSourcesAndDefaults() {
        String a = source("pump");
        String b = source("valve");

        RunRow run = runs.create(projectId, "REPLAY", null, null, List.of(a, b), null);

        assertThat(run.id()).isNotBlank();
        assertThat(run.projectId()).isEqualTo(projectId);
        assertThat(run.kind()).isEqualTo("REPLAY");
        assertThat(run.trigger()).isEqualTo("MANUAL"); // DB default
        assertThat(run.initiator()).isEqualTo("local"); // DB default
        assertThat(run.state()).isEqualTo("QUEUED"); // DB default
        assertThat(run.startedAt()).isNull();
        assertThat(run.endedAt()).isNull();
        assertThat(run.sourceIds()).containsExactlyInAnyOrder(a, b);

        assertThat(runs.findById(run.id())).contains(run);
    }

    @Test
    void duplicateSourceIdsAreDeduped() {
        String s = source("dup");

        RunRow run = runs.create(projectId, "REPLAY", null, null, List.of(s, s), null);

        assertThat(run.sourceIds()).containsExactly(s);
    }

    @Test
    void emptySourcesIsAllowed() {
        RunRow run = runs.create(projectId, "SYNTHETIC", "AUTOMATED", "scheduler", List.of(), null);

        assertThat(run.trigger()).isEqualTo("AUTOMATED");
        assertThat(run.initiator()).isEqualTo("scheduler");
        assertThat(run.sourceIds()).isEmpty();
    }

    @Test
    void lifecycleStartThenEnd() {
        RunRow run = runs.create(projectId, "REPLAY", null, null, List.of(source("s")), null);
        OffsetDateTime started = OffsetDateTime.parse("2026-04-01T10:00:00Z");
        OffsetDateTime ended = OffsetDateTime.parse("2026-04-01T10:05:00Z");

        RunRow running = runs.start(run.id(), started);
        assertThat(running.state()).isEqualTo("RUNNING");
        assertThat(running.startedAt()).isEqualTo(started);

        RunRow done = runs.end(run.id(), "COMPLETED", ended);
        assertThat(done.state()).isEqualTo("COMPLETED");
        assertThat(done.startedAt()).isEqualTo(started);
        assertThat(done.endedAt()).isEqualTo(ended);
    }

    @Test
    void linkEvidenceWiresBothSides() {
        RunRow run = runs.create(projectId, "RECORDING", null, null, List.of(), null);
        EvidenceRow ev = evidence.create(projectId, run.id(), "it");

        RunRow linked = runs.linkEvidence(run.id(), ev.id());

        assertThat(linked.evidenceId()).isEqualTo(ev.id());
        assertThat(evidence.findByRun(run.id())).map(EvidenceRow::id).contains(ev.id());
    }

    @Test
    void mutatingUnknownRunThrowsNotFound() {
        assertThatThrownBy(() -> runs.start("missing", OffsetDateTime.parse("2026-04-01T10:00:00Z")))
                .isInstanceOf(org.jooq.exception.NoDataFoundException.class);
    }

    @Test
    void childRunCarriesParentRunId() {
        RunRow parent = runs.create(projectId, "SCENARIO", "MANUAL", "local", List.of(), null, null);
        RunRow child = runs.create(projectId, "REPLAY", "MANUAL", "local", List.of(), null, parent.id());

        assertThat(parent.parentRunId()).isNull();
        assertThat(child.parentRunId()).isEqualTo(parent.id());
        assertThat(runs.findById(child.id())).get()
                .extracting(RunRow::parentRunId).isEqualTo(parent.id());
    }

    @Test
    void findByProjectReturnsNewestFirst() {
        String project = projects.insert("Ordering", null, "it").id();
        RunRow first = runs.create(project, "REPLAY", null, null, List.of(), null);
        RunRow second = runs.create(project, "SYNTHETIC", null, null, List.of(), null);

        List<RunRow> rows = runs.findByProject(project);

        assertThat(rows).extracting(RunRow::id).containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(rows).isSortedAccordingTo(comparing(RunRow::createdAt).reversed());
    }
}
