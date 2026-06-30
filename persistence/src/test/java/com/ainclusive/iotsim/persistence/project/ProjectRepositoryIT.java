package com.ainclusive.iotsim.persistence.project;

import static org.assertj.core.api.Assertions.assertThat;

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

    @Test
    void findAllPagedReturnsBatchNewestFirst() {
        ProjectRow a = repository.insert("Paged-A", null, "it");
        ProjectRow b = repository.insert("Paged-B", null, "it");
        ProjectRow c = repository.insert("Paged-C", null, "it");

        List<ProjectRow> page1 = repository.findAllPaged(null, null, null, 2);
        assertThat(page1).hasSize(2);
        // newest first
        assertThat(page1.get(0).id()).isEqualTo(c.id());
        assertThat(page1.get(1).id()).isEqualTo(b.id());

        // second page using keyset cursor from last item of page1
        ProjectRow last = page1.get(page1.size() - 1);
        List<ProjectRow> page2 = repository.findAllPaged(null, last.createdAt(), last.id(), 2);
        assertThat(page2).extracting(ProjectRow::id).contains(a.id());
        assertThat(page2).extracting(ProjectRow::id).doesNotContain(b.id(), c.id());

        repository.deleteById(a.id());
        repository.deleteById(b.id());
        repository.deleteById(c.id());
    }
}
