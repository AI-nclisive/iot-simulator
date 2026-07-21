package com.ainclusive.iotsim.domain.manualschema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.manualschema.ManualSchemaRepository;
import com.ainclusive.iotsim.persistence.manualschema.ManualSchemaRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.project.ProjectRow;
import com.ainclusive.iotsim.platform.Ids;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ManualSchemaServiceTest {

    private static final String PROJECT = "proj-1";

    private InMemoryManualSchemaRepository repo;
    private ManualSchemaService service;

    @BeforeEach
    void setUp() {
        repo = new InMemoryManualSchemaRepository();
        service = new ManualSchemaService(repo, new FakeProjectRepository(), new ObjectMapper());
    }

    private static List<SchemaNode> sampleNodes() {
        return List.of(
                new SchemaNode("f1", null, "Plant", "Plant", NodeKind.FOLDER, null, null, null, null, null),
                new SchemaNode("v1", "f1", "Plant/Temp", "Temp",
                        NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", null));
    }

    @Test
    void createThenGetRoundTripsNodes() {
        ManualSchema created = service.create(PROJECT, "OPC_UA", "Boiler", "desc", sampleNodes(), "it");
        assertThat(created.version()).isZero();
        assertThat(created.nodes()).hasSize(2);

        ManualSchema fetched = service.get(PROJECT, created.id());
        assertThat(fetched.name()).isEqualTo("Boiler");
        assertThat(fetched.nodes()).extracting(SchemaNode::nodeId).containsExactly("f1", "v1");
    }

    @Test
    void listPagedReturnsSchemasInProject() {
        service.create(PROJECT, "OPC_UA", "Boiler", null, sampleNodes(), "it");
        service.create(PROJECT, "OPC_UA", "Pump", null, sampleNodes(), "it");

        var page = service.listPaged(PROJECT, null, null);

        assertThat(page.items()).hasSize(2);
        assertThat(page.items()).extracting(ManualSchema::name).containsExactlyInAnyOrder("Boiler", "Pump");
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    void listPagedSecondPageContinuesFromCursor() {
        service.create(PROJECT, "OPC_UA", "A", null, sampleNodes(), "it");
        service.create(PROJECT, "OPC_UA", "B", null, sampleNodes(), "it");
        service.create(PROJECT, "OPC_UA", "C", null, sampleNodes(), "it");

        var page1 = service.listPaged(PROJECT, null, 2);
        assertThat(page1.items()).extracting(ManualSchema::name).containsExactly("C", "B");
        assertThat(page1.nextCursor()).isNotNull();

        var page2 = service.listPaged(PROJECT, page1.nextCursor(), 2);
        assertThat(page2.items()).extracting(ManualSchema::name).containsExactly("A");
        assertThat(page2.nextCursor()).isNull();
    }

    @Test
    void createWithInvalidProtocolIsRejected() {
        assertThatThrownBy(() -> service.create(PROJECT, "NOT_A_PROTOCOL", "X", null, sampleNodes(), "it"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithDuplicatePathIsRejected() {
        List<SchemaNode> dup = List.of(
                new SchemaNode("a", null, "Same", "A", NodeKind.FOLDER, null, null, null, null, null),
                new SchemaNode("b", null, "Same", "B", NodeKind.FOLDER, null, null, null, null, null));
        assertThatThrownBy(() -> service.create(PROJECT, "OPC_UA", "X", null, dup, "it"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getForWrongProjectThrowsNotFound() {
        ManualSchema created = service.create(PROJECT, "OPC_UA", "Boiler", null, sampleNodes(), "it");
        assertThatThrownBy(() -> service.get("other-project", created.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.get(PROJECT, "no-such-id"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateSavesInPlaceWithoutVersionChain() {
        ManualSchema created = service.create(PROJECT, "OPC_UA", "Boiler", null, sampleNodes(), "it");
        ManualSchema updated = service.update(
                PROJECT, created.id(), "Boiler v2", "updated", List.of(sampleNodes().get(0)), created.version());

        assertThat(updated.id()).isEqualTo(created.id());
        assertThat(updated.name()).isEqualTo("Boiler v2");
        assertThat(updated.nodes()).hasSize(1);
        assertThat(updated.version()).isEqualTo(1);
    }

    @Test
    void updateWithStaleVersionThrowsConcurrencyConflict() {
        ManualSchema created = service.create(PROJECT, "OPC_UA", "Boiler", null, sampleNodes(), "it");
        assertThatThrownBy(() -> service.update(PROJECT, created.id(), "x", null, sampleNodes(), 999))
                .isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void duplicateCreatesIndependentCopy() {
        ManualSchema created = service.create(PROJECT, "OPC_UA", "Boiler", "desc", sampleNodes(), "it");
        ManualSchema copy = service.duplicate(PROJECT, created.id(), "Boiler (copy)", "it");

        assertThat(copy.id()).isNotEqualTo(created.id());
        assertThat(copy.name()).isEqualTo("Boiler (copy)");
        assertThat(copy.nodes()).isEqualTo(created.nodes());
        assertThat(copy.version()).isZero();

        // Editing the source afterwards must not affect the already-made copy.
        service.update(PROJECT, created.id(), "Renamed", null, List.of(), created.version());
        assertThat(service.get(PROJECT, copy.id()).name()).isEqualTo("Boiler (copy)");
    }

    @Test
    void deleteRemovesSchema() {
        ManualSchema created = service.create(PROJECT, "OPC_UA", "Boiler", null, sampleNodes(), "it");
        service.delete(PROJECT, created.id());
        assertThatThrownBy(() -> service.get(PROJECT, created.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    private static final class InMemoryManualSchemaRepository implements ManualSchemaRepository {
        private final Map<String, ManualSchemaRow> rows = new HashMap<>();
        // Strictly-increasing offset so rows created within the same test get distinct,
        // deterministically ordered createdAt values regardless of system clock resolution —
        // needed for findByProjectPaged's keyset ordering/tie-break to be exercised reliably.
        private long sequence = 0;

        @Override
        public ManualSchemaRow create(String projectId, String protocol, String name, String description,
                String nodesJson, String createdBy) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC).plusNanos(sequence++);
            ManualSchemaRow row = new ManualSchemaRow(
                    Ids.newId(), projectId, protocol, name, description, nodesJson, now, now, createdBy, 0);
            rows.put(row.id(), row);
            return row;
        }

        @Override
        public List<ManualSchemaRow> findByProject(String projectId) {
            return rows.values().stream().filter(r -> r.projectId().equals(projectId)).toList();
        }

        /** Mirrors JooqManualSchemaRepository's keyset semantics: created_at DESC, id DESC. */
        @Override
        public List<ManualSchemaRow> findByProjectPaged(String projectId, OffsetDateTime afterAt,
                String afterId, int limit) {
            return findByProject(projectId).stream()
                    .filter(r -> afterAt == null
                            || r.createdAt().isBefore(afterAt)
                            || (r.createdAt().isEqual(afterAt) && r.id().compareTo(afterId) < 0))
                    .sorted((a, b) -> {
                        int cmp = b.createdAt().compareTo(a.createdAt());
                        return cmp != 0 ? cmp : b.id().compareTo(a.id());
                    })
                    .limit(limit)
                    .toList();
        }

        @Override
        public Optional<ManualSchemaRow> findById(String id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public Optional<ManualSchemaRow> update(String id, String name, String description,
                String nodesJson, long expectedVersion) {
            ManualSchemaRow current = rows.get(id);
            if (current == null || current.version() != expectedVersion) {
                return Optional.empty();
            }
            ManualSchemaRow updated = new ManualSchemaRow(current.id(), current.projectId(), current.protocol(),
                    name, description, nodesJson, current.createdAt(),
                    OffsetDateTime.now(ZoneOffset.UTC), current.createdBy(), current.version() + 1);
            rows.put(id, updated);
            return Optional.of(updated);
        }

        @Override
        public Optional<ManualSchemaRow> duplicate(String sourceId, String newName, String createdBy) {
            ManualSchemaRow source = rows.get(sourceId);
            if (source == null) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            ManualSchemaRow copy = new ManualSchemaRow(Ids.newId(), source.projectId(), source.protocol(),
                    newName, source.description(), source.nodesJson(), now, now, createdBy, 0);
            rows.put(copy.id(), copy);
            return Optional.of(copy);
        }

        @Override
        public boolean deleteById(String id) {
            return rows.remove(id) != null;
        }
    }

    private static final class FakeProjectRepository implements ProjectRepository {
        @Override
        public Optional<ProjectRow> findById(String id) {
            if (!PROJECT.equals(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new ProjectRow(id, "p", null, "ACTIVE", now, now, "local", 0));
        }

        @Override
        public ProjectRow insert(String name, String description, String createdBy) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<ProjectRow> findAll() {
            return List.of();
        }

        @Override
        public List<ProjectRow> findAllPaged(String status, OffsetDateTime afterAt, String afterId, int limit) {
            return List.of();
        }

        @Override
        public Optional<ProjectRow> update(String id, String name, String description, long expectedVersion) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<ProjectRow> archive(String id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean deleteById(String id) {
            throw new UnsupportedOperationException();
        }
    }
}
