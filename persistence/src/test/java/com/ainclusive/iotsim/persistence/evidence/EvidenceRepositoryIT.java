package com.ainclusive.iotsim.persistence.evidence;

import static java.util.Comparator.comparing;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.persistence.project.JooqProjectRepository;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Evidence record lifecycle against real Postgres (backend-specs/04, IS-050). */
@Testcontainers(disabledWithoutDocker = true)
class EvidenceRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static DSLContext dsl;
    static JooqProjectRepository projects;
    static EvidenceRepository evidence;
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
        evidence = new JooqEvidenceRepository(dsl);
        projectId = projects.insert("Plant", null, "it").id();
    }

    @Test
    void createOpensCapturingWithDefaults() {
        EvidenceRow ev = evidence.create(projectId, null, null);

        assertThat(ev.id()).isNotBlank();
        assertThat(ev.projectId()).isEqualTo(projectId);
        assertThat(ev.runId()).isNull();
        assertThat(ev.status()).isEqualTo("CAPTURING"); // DB default
        assertThat(ev.manifestJson()).isEqualTo("{}"); // DB default
        assertThat(ev.objectRef()).isNull();
        assertThat(ev.createdBy()).isEqualTo("local"); // DB default
        assertThat(ev.createdAt()).isNotNull();

        assertThat(evidence.findById(ev.id())).contains(ev);
    }

    @Test
    void updateManifestReplacesContent() {
        EvidenceRow ev = evidence.create(projectId, null, "it");

        EvidenceRow updated = evidence.updateManifest(ev.id(), "{\"valueCount\":42}");

        assertThat(updated.manifestJson()).contains("\"valueCount\": 42");
        assertThat(updated.status()).isEqualTo("CAPTURING"); // untouched
    }

    @Test
    void updateStatusRecordsTerminalStateAndObjectRef() {
        EvidenceRow ev = evidence.create(projectId, null, "it");

        EvidenceRow ready = evidence.updateStatus(ev.id(), "READY", "object://bucket/run-1.zip");

        assertThat(ready.status()).isEqualTo("READY");
        assertThat(ready.objectRef()).isEqualTo("object://bucket/run-1.zip");

        EvidenceRow failed = evidence.updateStatus(ev.id(), "EXPORT_FAILED", null);
        assertThat(failed.status()).isEqualTo("EXPORT_FAILED");
        assertThat(failed.objectRef()).isNull();
    }

    @Test
    void updatingUnknownEvidenceThrowsNotFound() {
        assertThatThrownBy(() -> evidence.updateStatus("missing", "READY", null))
                .isInstanceOf(org.jooq.exception.NoDataFoundException.class);
    }

    @Test
    void findByProjectReturnsNewestFirst() {
        String project = projects.insert("Ordering", null, "it").id();
        EvidenceRow first = evidence.create(project, null, "it");
        EvidenceRow second = evidence.create(project, null, "it");

        List<EvidenceRow> rows = evidence.findByProject(project);

        assertThat(rows).extracting(EvidenceRow::id).containsExactlyInAnyOrder(first.id(), second.id());
        assertThat(rows).isSortedAccordingTo(comparing(EvidenceRow::createdAt).reversed());
    }

    @Test
    void findByProjectPagedReturnsBatchNewestFirst() {
        String project = projects.insert("Paged", null, "it").id();
        EvidenceRow a = evidence.create(project, null, "it");
        EvidenceRow b = evidence.create(project, null, "it");
        EvidenceRow c = evidence.create(project, null, "it");

        List<EvidenceRow> page1 = evidence.findByProjectPaged(project, null, null, 2);
        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).id()).isEqualTo(c.id());
        assertThat(page1.get(1).id()).isEqualTo(b.id());

        EvidenceRow last = page1.get(page1.size() - 1);
        List<EvidenceRow> page2 = evidence.findByProjectPaged(project, last.createdAt(), last.id(), 2);
        assertThat(page2).extracting(EvidenceRow::id).contains(a.id());
        assertThat(page2).extracting(EvidenceRow::id).doesNotContain(b.id(), c.id());
    }
}
