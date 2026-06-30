package com.ainclusive.iotsim.persistence.sample;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.project.JooqProjectRepository;
import com.ainclusive.iotsim.persistence.project.ProjectRow;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises {@link JooqSampleRepository} (incl. JSONB selection/tags) against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
class SampleRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static SampleRepository samples;
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
        samples = new JooqSampleRepository(dsl);
    }

    @Test
    void createFindDeleteWithJsonbRoundTrip() {
        String sel = "{\"nodeIds\":[\"n1\",\"n2\"]}";
        String tags = "[\"env\",\"prod\"]";

        SampleRow created = samples.create(projectId, null, "Baseline", sel, tags, "it");

        assertThat(created.id()).isNotBlank();
        assertThat(created.projectId()).isEqualTo(projectId);
        assertThat(created.name()).isEqualTo("Baseline");
        assertThat(created.selection()).contains("nodeIds").contains("n1");
        assertThat(created.tags()).contains("env").contains("prod");
        assertThat(created.derivedFromRecordingId()).isNull();
        assertThat(created.version()).isZero();

        assertThat(samples.findById(created.id())).isPresent();
        List<SampleRow> all = samples.findByProject(projectId);
        assertThat(all).extracting(SampleRow::id).contains(created.id());

        assertThat(samples.deleteById(created.id())).isTrue();
        assertThat(samples.findById(created.id())).isEmpty();
    }

    @Test
    void createWithNullSelectionAndTagsFallsBackToDefaults() {
        SampleRow row = samples.create(projectId, null, "Empty", null, null, "it");
        assertThat(row.selection()).isEqualTo("{}");
        assertThat(row.tags()).isEqualTo("[]");
        samples.deleteById(row.id());
    }

    @Test
    void findByProjectReturnsOnlyMatchingProject() {
        SampleRow s1 = samples.create(projectId, null, "S1", null, null, "it");
        SampleRow s2 = samples.create(projectId, null, "S2", null, null, "it");

        List<SampleRow> result = samples.findByProject(projectId);
        assertThat(result).extracting(SampleRow::id).contains(s1.id(), s2.id());
        assertThat(samples.findByProject("other-project")).isEmpty();

        samples.deleteById(s1.id());
        samples.deleteById(s2.id());
    }

    @Test
    void deleteOnMissingIdReturnsFalse() {
        assertThat(samples.deleteById("no-such-id")).isFalse();
    }

    @Test
    void findByProjectPagedReturnsBatchNewestFirst() {
        SampleRow a = samples.create(projectId, null, "Paged-A", null, null, "it");
        SampleRow b = samples.create(projectId, null, "Paged-B", null, null, "it");
        SampleRow c = samples.create(projectId, null, "Paged-C", null, null, "it");

        List<SampleRow> page1 = samples.findByProjectPaged(projectId, null, null, 2);
        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).id()).isEqualTo(c.id());
        assertThat(page1.get(1).id()).isEqualTo(b.id());

        SampleRow last = page1.get(page1.size() - 1);
        List<SampleRow> page2 = samples.findByProjectPaged(projectId, last.createdAt(), last.id(), 2);
        assertThat(page2).extracting(SampleRow::id).contains(a.id());
        assertThat(page2).extracting(SampleRow::id).doesNotContain(b.id(), c.id());

        samples.deleteById(a.id());
        samples.deleteById(b.id());
        samples.deleteById(c.id());
    }
}
