package com.ainclusive.iotsim.persistence.manualschema;

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

/** Exercises the jOOQ manual-schema repository (incl. JSONB nodes) against real Postgres (IS-171). */
@Testcontainers(disabledWithoutDocker = true)
class ManualSchemaRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static ManualSchemaRepository manualSchemas;
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
        manualSchemas = new JooqManualSchemaRepository(dsl);
    }

    private static final String NODES_JSON = "[{\"nodeId\":\"n1\",\"parentId\":null,\"path\":\"/n1\","
            + "\"name\":\"n1\",\"kind\":\"VARIABLE\",\"dataType\":\"FLOAT64\",\"valueRank\":\"SCALAR\","
            + "\"access\":\"READ\",\"unit\":null,\"description\":null}]";

    @Test
    void createFindUpdateDeleteWithJsonb() {
        ManualSchemaRow row = manualSchemas.create(
                projectId, "OPC_UA", "Boiler template", "A reusable boiler layout", NODES_JSON, "it");

        assertThat(row.id()).isNotBlank();
        assertThat(row.protocol()).isEqualTo("OPC_UA");
        assertThat(row.name()).isEqualTo("Boiler template");
        assertThat(row.description()).isEqualTo("A reusable boiler layout");
        assertThat(row.nodesJson()).contains("\"n1\"");
        assertThat(row.version()).isZero();

        assertThat(manualSchemas.findByProject(projectId)).extracting(ManualSchemaRow::id).contains(row.id());
        assertThat(manualSchemas.findById(row.id())).isPresent();

        Optional<ManualSchemaRow> updated = manualSchemas.update(
                row.id(), "Boiler template v2", "Updated", "[]", 0);
        assertThat(updated).isPresent();
        assertThat(updated.get().name()).isEqualTo("Boiler template v2");
        assertThat(updated.get().description()).isEqualTo("Updated");
        assertThat(updated.get().nodesJson()).isEqualTo("[]");
        assertThat(updated.get().version()).isEqualTo(1L);

        assertThat(manualSchemas.deleteById(row.id())).isTrue();
        assertThat(manualSchemas.findById(row.id())).isEmpty();
    }

    @Test
    void updateWithStaleVersionMatchesNothing() {
        ManualSchemaRow created = manualSchemas.create(projectId, "OPC_UA", "Stale", null, "[]", "it");
        assertThat(manualSchemas.update(created.id(), "x", null, "[]", 999)).isEmpty();
        manualSchemas.deleteById(created.id());
    }

    @Test
    void duplicateCreatesNewRowWithSameProtocolAndNodes() {
        ManualSchemaRow source = manualSchemas.create(
                projectId, "OPC_UA", "Pump layout", "desc", NODES_JSON, "it");

        Optional<ManualSchemaRow> copy = manualSchemas.duplicate(source.id(), "Pump layout (copy)", "it");

        assertThat(copy).isPresent();
        assertThat(copy.get().id()).isNotEqualTo(source.id());
        assertThat(copy.get().name()).isEqualTo("Pump layout (copy)");
        assertThat(copy.get().projectId()).isEqualTo(projectId);
        assertThat(copy.get().protocol()).isEqualTo("OPC_UA");
        assertThat(copy.get().description()).isEqualTo(source.description());
        assertThat(copy.get().nodesJson()).isEqualTo(source.nodesJson());
        assertThat(copy.get().version()).isZero();

        manualSchemas.deleteById(source.id());
        manualSchemas.deleteById(copy.get().id());
    }

    @Test
    void duplicateOnMissingSourceReturnsEmpty() {
        assertThat(manualSchemas.duplicate("no-such-id", "Copy", "it")).isEmpty();
    }

    @Test
    void createWithoutNodesDefaultsToEmptyArray() {
        ManualSchemaRow row = manualSchemas.create(projectId, "OPC_UA", "Blank", null, null, "it");
        assertThat(row.nodesJson()).isEqualTo("[]");
        manualSchemas.deleteById(row.id());
    }

    @Test
    void findByProjectPagedReturnsBatchNewestFirst() {
        ManualSchemaRow a = manualSchemas.create(projectId, "OPC_UA", "Paged-A", null, "[]", "it");
        ManualSchemaRow b = manualSchemas.create(projectId, "OPC_UA", "Paged-B", null, "[]", "it");
        ManualSchemaRow c = manualSchemas.create(projectId, "OPC_UA", "Paged-C", null, "[]", "it");

        List<ManualSchemaRow> page1 = manualSchemas.findByProjectPaged(projectId, null, null, 2);
        assertThat(page1).hasSize(2);
        assertThat(page1.get(0).id()).isEqualTo(c.id());
        assertThat(page1.get(1).id()).isEqualTo(b.id());

        ManualSchemaRow last = page1.get(page1.size() - 1);
        List<ManualSchemaRow> page2 = manualSchemas.findByProjectPaged(
                projectId, last.createdAt(), last.id(), 2);
        assertThat(page2).extracting(ManualSchemaRow::id).contains(a.id());
        assertThat(page2).extracting(ManualSchemaRow::id).doesNotContain(b.id(), c.id());

        manualSchemas.deleteById(a.id());
        manualSchemas.deleteById(b.id());
        manualSchemas.deleteById(c.id());
    }

    @Test
    void findByProjectReturnsOnlyThatProjectsSchemas() {
        ProjectRow otherProject;
        DSLContext dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        otherProject = new JooqProjectRepository(dsl).insert("Other plant", null, "it");

        ManualSchemaRow inProject = manualSchemas.create(projectId, "OPC_UA", "Mine", null, "[]", "it");
        ManualSchemaRow inOther = manualSchemas.create(otherProject.id(), "OPC_UA", "Theirs", null, "[]", "it");

        List<ManualSchemaRow> mine = manualSchemas.findByProject(projectId);
        assertThat(mine).extracting(ManualSchemaRow::id).contains(inProject.id());
        assertThat(mine).extracting(ManualSchemaRow::id).doesNotContain(inOther.id());

        manualSchemas.deleteById(inProject.id());
        manualSchemas.deleteById(inOther.id());
    }
}
