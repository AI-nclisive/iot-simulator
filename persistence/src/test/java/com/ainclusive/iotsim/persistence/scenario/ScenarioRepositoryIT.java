package com.ainclusive.iotsim.persistence.scenario;

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

/** Exercises {@link JooqScenarioRepository} (incl. ordered steps + JSONB) against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
class ScenarioRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static ScenarioRepository scenarios;
    static String projectId;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DSLContext dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ProjectRow project = new JooqProjectRepository(dsl).insert("Factory", null, "it");
        projectId = project.id();
        scenarios = new JooqScenarioRepository(dsl);
    }

    @Test
    void createPersistsScenarioAndOrderedSteps() {
        List<ScenarioStepInput> steps = List.of(
                new ScenarioStepInput("START", "ds-1", "{}"),
                new ScenarioStepInput("WAIT", null, "{\"ms\":500}"),
                new ScenarioStepInput("STOP", "ds-1", "{}"));

        ScenarioRow row = scenarios.create(projectId, "Flow", "{\"seed\":7}", steps, "it");

        assertThat(row.id()).isNotBlank();
        assertThat(row.status()).isEqualTo("DRAFT");
        assertThat(row.version()).isZero();
        assertThat(row.deterministicSettings()).contains("seed");
        assertThat(row.steps()).extracting(ScenarioStepRow::ordinal).containsExactly(0, 1, 2);
        assertThat(row.steps()).extracting(ScenarioStepRow::type).containsExactly("START", "WAIT", "STOP");
        assertThat(row.steps().get(1).params()).contains("ms");

        scenarios.deleteById(row.id());
    }

    @Test
    void createWithNullJsonFallsBackToEmptyObject() {
        ScenarioRow row = scenarios.create(projectId, "Empty", null, List.of(), "it");
        assertThat(row.deterministicSettings()).isEqualTo("{}");
        assertThat(row.steps()).isEmpty();
        scenarios.deleteById(row.id());
    }

    @Test
    void updateReplacesStepsAndBumpsVersionWhenVersionMatches() {
        ScenarioRow created = scenarios.create(projectId, "V", "{}",
                List.of(new ScenarioStepInput("MARKER", null, "{}")), "it");

        Optional<ScenarioRow> updated = scenarios.update(created.id(), "V2", null,
                List.of(new ScenarioStepInput("START", "ds-9", "{}"),
                        new ScenarioStepInput("STOP", "ds-9", "{}")),
                created.version());

        assertThat(updated).isPresent();
        assertThat(updated.get().name()).isEqualTo("V2");
        assertThat(updated.get().version()).isEqualTo(created.version() + 1);
        assertThat(updated.get().steps()).extracting(ScenarioStepRow::type)
                .containsExactly("START", "STOP");
        scenarios.deleteById(created.id());
    }

    @Test
    void updateWithNullStepsKeepsExistingSteps() {
        ScenarioRow created = scenarios.create(projectId, "Keep", "{}",
                List.of(new ScenarioStepInput("MARKER", null, "{}")), "it");

        Optional<ScenarioRow> updated = scenarios.update(created.id(), "KeepRenamed", null,
                null, created.version());

        assertThat(updated).isPresent();
        assertThat(updated.get().name()).isEqualTo("KeepRenamed");
        assertThat(updated.get().steps()).extracting(ScenarioStepRow::type).containsExactly("MARKER");
        scenarios.deleteById(created.id());
    }

    @Test
    void updateWithStaleVersionReturnsEmpty() {
        ScenarioRow created = scenarios.create(projectId, "Stale", "{}", List.of(), "it");
        assertThat(scenarios.update(created.id(), "X", null, null, created.version() + 99)).isEmpty();
        scenarios.deleteById(created.id());
    }

    @Test
    void findByProjectPagedNewestFirstAndDeleteCascadesSteps() {
        ScenarioRow a = scenarios.create(projectId, "A", "{}",
                List.of(new ScenarioStepInput("MARKER", null, "{}")), "it");
        ScenarioRow b = scenarios.create(projectId, "B", "{}", List.of(), "it");

        List<ScenarioRow> page1 = scenarios.findByProjectPaged(projectId, null, null, 1);
        assertThat(page1).hasSize(1);
        assertThat(page1.get(0).id()).isEqualTo(b.id());

        assertThat(scenarios.deleteById(a.id())).isTrue();
        assertThat(scenarios.findById(a.id())).isEmpty();
        scenarios.deleteById(b.id());
    }
}
