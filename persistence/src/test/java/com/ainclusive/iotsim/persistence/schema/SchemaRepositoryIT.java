package com.ainclusive.iotsim.persistence.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.datasource.JooqDataSourceRepository;
import com.ainclusive.iotsim.persistence.project.JooqProjectRepository;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
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

/** Schema versioning + node round-trip against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
class SchemaRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static SchemaRepository schemas;
    static String dataSourceId;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DSLContext dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        String projectId = new JooqProjectRepository(dsl).insert("Plant", null, "it").id();
        DataSourceRow source = new JooqDataSourceRepository(dsl)
                .insert(projectId, "Pump", "OPC_UA", "MANUAL", 4840, null, null, null, "it");
        dataSourceId = source.id();
        schemas = new JooqSchemaRepository(dsl);
    }

    private static List<SchemaNode> sampleNodes() {
        return List.of(
                new SchemaNode("f1", null, "Plant", "Plant", NodeKind.FOLDER, null, null, null, null, null),
                new SchemaNode("v1", "f1", "Plant/Temp", "Temp",
                        NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", null));
    }

    @Test
    void saveAndReadBackVersionedSchema() {
        SchemaWithNodes v1 = schemas.saveNewVersion(dataSourceId, sampleNodes());
        assertThat(v1.version()).isEqualTo(1);

        Optional<SchemaWithNodes> current = schemas.findCurrent(dataSourceId);
        assertThat(current).isPresent();
        assertThat(current.get().version()).isEqualTo(1);
        assertThat(current.get().nodes()).hasSize(2);
        SchemaNode variable = current.get().nodes().stream()
                .filter(n -> n.kind() == NodeKind.VARIABLE).findFirst().orElseThrow();
        assertThat(variable.dataType()).isEqualTo(DataType.FLOAT64);
        assertThat(variable.path()).isEqualTo("Plant/Temp");

        SchemaWithNodes v2 = schemas.saveNewVersion(dataSourceId, sampleNodes());
        assertThat(v2.version()).isEqualTo(2);
        assertThat(schemas.findCurrent(dataSourceId).orElseThrow().version()).isEqualTo(2);
    }
}
