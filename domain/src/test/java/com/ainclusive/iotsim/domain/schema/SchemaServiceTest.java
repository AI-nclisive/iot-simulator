package com.ainclusive.iotsim.domain.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.common.SchemaImpactException;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SchemaServiceTest {

    private static final String PROJECT = "proj-1";
    private static final String SOURCE = "ds-1";

    private InMemorySchemaRepository schemaRepo;
    private FakeDataSourceRepository dataSourceRepo;
    private SchemaService service;

    @BeforeEach
    void setUp() {
        schemaRepo = new InMemorySchemaRepository();
        dataSourceRepo = new FakeDataSourceRepository(SOURCE, PROJECT);
        service = new SchemaService(schemaRepo, dataSourceRepo, new ObjectMapper());
    }

    private static List<SchemaNode> sampleNodes() {
        return List.of(
                new SchemaNode("f1", null, "Plant", "Plant", NodeKind.FOLDER, null, null, null, null, null),
                new SchemaNode("v1", "f1", "Plant/Temp", "Temp",
                        NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", null));
    }

    @Test
    void getWithoutSchemaThrowsNotFound() {
        assertThatThrownBy(() -> service.get(PROJECT, SOURCE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void saveCreatesIncrementingVersions() {
        Schema v1 = service.save(PROJECT, SOURCE, sampleNodes());
        assertThat(v1.version()).isEqualTo(1);
        assertThat(v1.nodes()).hasSize(2);

        Schema v2 = service.save(PROJECT, SOURCE, sampleNodes());
        assertThat(v2.version()).isEqualTo(2);

        assertThat(service.get(PROJECT, SOURCE).version()).isEqualTo(2);
    }

    @Test
    void getForWrongProjectThrowsNotFound() {
        service.save(PROJECT, SOURCE, sampleNodes());
        assertThatThrownBy(() -> service.get("other", SOURCE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void saveWithDuplicatePathIsRejected() {
        List<SchemaNode> dup = List.of(
                new SchemaNode("a", null, "Same", "A", NodeKind.FOLDER, null, null, null, null, null),
                new SchemaNode("b", null, "Same", "B", NodeKind.FOLDER, null, null, null, null, null));
        assertThatThrownBy(() -> service.save(PROJECT, SOURCE, dup))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void removingNodeStillDrivenByOwnSyntheticConfigIsRejected() {
        dataSourceRepo.basis = "SYNTHETIC";
        dataSourceRepo.runtimeConfig = "{\"variables\":[{\"nodeId\":\"v1\",\"dataType\":\"FLOAT64\"}]}";
        service.save(PROJECT, SOURCE, sampleNodes());

        List<SchemaNode> withoutV1 = List.of(sampleNodes().get(0));
        assertThatThrownBy(() -> service.save(PROJECT, SOURCE, withoutV1))
                .isInstanceOf(SchemaImpactException.class)
                .satisfies(e -> assertThat(((SchemaImpactException) e).issues())
                        .anyMatch(issue -> issue.contains("v1")));

        // rejected save must not have persisted a new version
        assertThat(service.get(PROJECT, SOURCE).version()).isEqualTo(1);
    }

    @Test
    void removingUnreferencedNodeOnSyntheticSourceSucceeds() {
        dataSourceRepo.basis = "SYNTHETIC";
        dataSourceRepo.runtimeConfig = "{\"variables\":[{\"nodeId\":\"other\",\"dataType\":\"FLOAT64\"}]}";
        service.save(PROJECT, SOURCE, sampleNodes());

        List<SchemaNode> withoutV1 = List.of(sampleNodes().get(0));
        Schema v2 = service.save(PROJECT, SOURCE, withoutV1);
        assertThat(v2.version()).isEqualTo(2);
    }

    @Test
    void nonSyntheticSourceSkipsImpactCheck() {
        service.save(PROJECT, SOURCE, sampleNodes());
        List<SchemaNode> withoutV1 = List.of(sampleNodes().get(0));
        Schema v2 = service.save(PROJECT, SOURCE, withoutV1);
        assertThat(v2.version()).isEqualTo(2);
    }

    @Test
    void retypingNodeStillDrivenByOwnSyntheticConfigIsRejected() {
        dataSourceRepo.basis = "SYNTHETIC";
        dataSourceRepo.runtimeConfig = "{\"variables\":[{\"nodeId\":\"v1\",\"dataType\":\"FLOAT64\"}]}";
        service.save(PROJECT, SOURCE, sampleNodes());

        // v1 kept, but retyped to INT32 — the config's FLOAT64 variable is now driving a mismatched node.
        List<SchemaNode> retyped = List.of(
                sampleNodes().get(0),
                new SchemaNode("v1", "f1", "Plant/Temp", "Temp",
                        NodeKind.VARIABLE, DataType.INT32, ValueRank.SCALAR, Access.READ, "degC", null));
        assertThatThrownBy(() -> service.save(PROJECT, SOURCE, retyped))
                .isInstanceOf(SchemaImpactException.class)
                .satisfies(e -> assertThat(((SchemaImpactException) e).issues())
                        .anyMatch(issue -> issue.contains("v1")));
    }

    @Test
    void malformedRuntimeConfigOnBreakingSaveFailsLoudInsteadOfSilentlySkipping() {
        dataSourceRepo.basis = "SYNTHETIC";
        dataSourceRepo.runtimeConfig = "not json";
        // First save: no current version yet, so the impact check never parses the config.
        service.save(PROJECT, SOURCE, sampleNodes());

        List<SchemaNode> withoutV1 = List.of(sampleNodes().get(0));
        assertThatThrownBy(() -> service.save(PROJECT, SOURCE, withoutV1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid synthetic runtimeConfig");
    }

    private static final class InMemorySchemaRepository implements SchemaRepository {
        private final Map<String, List<SchemaWithNodes>> byDataSource = new HashMap<>();

        @Override
        public Optional<SchemaWithNodes> findCurrent(String dataSourceId) {
            List<SchemaWithNodes> versions = byDataSource.get(dataSourceId);
            return versions == null || versions.isEmpty()
                    ? Optional.empty()
                    : Optional.of(versions.get(versions.size() - 1));
        }

        @Override
        public SchemaWithNodes saveNewVersion(String dataSourceId, List<SchemaNode> nodes) {
            List<SchemaWithNodes> versions = byDataSource.computeIfAbsent(dataSourceId, k -> new ArrayList<>());
            int next = versions.size() + 1;
            SchemaWithNodes saved = new SchemaWithNodes(
                    "sid-" + next, dataSourceId, next, OffsetDateTime.now(ZoneOffset.UTC), List.copyOf(nodes));
            versions.add(saved);
            return saved;
        }
    }

    private static final class FakeDataSourceRepository implements DataSourceRepository {
        private final String id;
        private final String projectId;
        private String basis = "MANUAL";
        private String runtimeConfig = "{}";

        FakeDataSourceRepository(String id, String projectId) {
            this.id = id;
            this.projectId = projectId;
        }

        @Override
        public Optional<DataSourceRow> findById(String id) {
            if (!this.id.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new DataSourceRow(id, projectId, "src", "OPC_UA", basis,
                    null, null, 0, null, runtimeConfig, null, false, now, now, "local", 0));
        }

        @Override
        public DataSourceRow insert(String projectId, String name, String protocol, String basis,
                int simulatorPort, String realDeviceEndpoint, String runtimeConfigJson,
                String securityConfigJson, String createdBy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<DataSourceRow> findByProject(String projectId) {
            return List.of();
        }

        @Override
        public List<DataSourceRow> findByProjectPaged(String projectId, String protocol,
                java.time.OffsetDateTime afterAt, String afterId, int limit) {
            return List.of();
        }

        @Override
        public Optional<DataSourceRow> update(String id, String name, int simulatorPort,
                String realDeviceEndpoint, String runtimeConfigJson, String securityConfigJson,
                boolean enabled, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<DataSourceRow> duplicate(String sourceId, String newName, String createdBy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }
    }
}
