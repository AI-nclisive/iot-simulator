package com.ainclusive.iotsim.persistence.schema;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.datasource.JooqDataSourceRepository;
import com.ainclusive.iotsim.persistence.project.JooqProjectRepository;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.util.List;
import java.util.Map;
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
    static JooqDataSourceRepository dataSources;
    static String dataSourceId;
    static String projectId;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DSLContext dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        projectId = new JooqProjectRepository(dsl).insert("Plant", null, "it").id();
        dataSources = new JooqDataSourceRepository(dsl);
        dataSourceId = dataSources.insert(projectId, "Pump", "OPC_UA", "MANUAL", 4840, null, null, null, "it").id();
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

    @Test
    void findByVersionReturnsSavedSchema() {
        SchemaWithNodes saved = schemas.saveNewVersion(dataSourceId, sampleNodes());
        Optional<SchemaWithNodes> found = schemas.findByVersion(dataSourceId, saved.version());
        assertThat(found).isPresent();
        assertThat(found.get().version()).isEqualTo(saved.version());
        assertThat(found.get().nodes()).hasSize(2);
    }

    @Test
    void findByVersionReturnsEmptyForMissingVersion() {
        assertThat(schemas.findByVersion(dataSourceId, 99999)).isEmpty();
    }

    /**
     * IT cases for countVariableNodesBySource (IS-149):
     * (a) a source with VARIABLE nodes returns the correct count (non-VARIABLE FOLDER nodes excluded),
     * (b) a source with no schema returns 0.
     */
    @Test
    void countVariableNodesBySource_returnsOnlyVariableNodeCount() {
        // Create dedicated sources so this test is independent of other test state.
        String sourceWithSchema = dataSources
                .insert(projectId, "Motor", "OPC_UA", "MANUAL", 4840, null, null, null, "it")
                .id();
        String sourceWithoutSchema = dataSources
                .insert(projectId, "Valve", "OPC_UA", "MANUAL", 4840, null, null, null, "it")
                .id();

        // Save a schema with 3 VARIABLE nodes + 2 non-VARIABLE (FOLDER) nodes.
        List<SchemaNode> mixed = List.of(
                new SchemaNode("fld1", null, "Root", "Root",
                        NodeKind.FOLDER, null, null, null, null, null),
                new SchemaNode("fld2", "fld1", "Root/Sub", "Sub",
                        NodeKind.FOLDER, null, null, null, null, null),
                new SchemaNode("var1", "fld1", "Root/Temp", "Temp",
                        NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", null),
                new SchemaNode("var2", "fld1", "Root/Pressure", "Pressure",
                        NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "bar", null),
                new SchemaNode("var3", "fld1", "Root/Speed", "Speed",
                        NodeKind.VARIABLE, DataType.INT32, ValueRank.SCALAR, Access.READ, "rpm", null));
        schemas.saveNewVersion(sourceWithSchema, mixed);

        Map<String, Integer> counts = schemas.countVariableNodesBySource(
                List.of(sourceWithSchema, sourceWithoutSchema));

        // (a) source with 3 VARIABLE + 2 FOLDER nodes => count = 3 (non-VARIABLE excluded)
        assertThat(counts.get(sourceWithSchema)).isEqualTo(3);

        // (b) source with no schema => count = 0 (not absent, not an exception)
        assertThat(counts.get(sourceWithoutSchema)).isEqualTo(0);

        // Both ids present in the result map
        assertThat(counts).containsKeys(sourceWithSchema, sourceWithoutSchema);
    }
}
