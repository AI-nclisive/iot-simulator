package com.epam.iotsim.persistence.project;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises the jOOQ repository against a real Postgres. Skipped gracefully when
 * no Docker daemon is available (so the offline build stays green).
 */
@Testcontainers(disabledWithoutDocker = true)
class ProjectRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static ProjectRepository repository;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DSLContext dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        repository = new JooqProjectRepository(dsl);
    }

    @Test
    void insertFindUpdateDelete() {
        ProjectRow created = repository.insert("Line 1", "desc", "alice");
        assertThat(created.id()).isNotBlank();
        assertThat(created.status()).isEqualTo("ACTIVE");
        assertThat(created.version()).isZero();

        assertThat(repository.findById(created.id())).isPresent();
        assertThat(repository.findAll()).extracting(ProjectRow::id).contains(created.id());

        Optional<ProjectRow> updated = repository.update(created.id(), "Line 1b", "changed", 0);
        assertThat(updated).isPresent();
        assertThat(updated.get().name()).isEqualTo("Line 1b");
        assertThat(updated.get().version()).isEqualTo(1L);

        assertThat(repository.deleteById(created.id())).isTrue();
        assertThat(repository.findById(created.id())).isEmpty();
    }

    @Test
    void updateWithStaleVersionMatchesNothing() {
        ProjectRow created = repository.insert("Stale", null, "bob");
        Optional<ProjectRow> result = repository.update(created.id(), "x", null, 999);
        assertThat(result).isEmpty();
    }
}
