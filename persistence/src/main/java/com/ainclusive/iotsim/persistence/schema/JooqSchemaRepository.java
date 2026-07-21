package com.ainclusive.iotsim.persistence.schema;

import static com.ainclusive.iotsim.persistence.jooq.tables.DataSources.DATA_SOURCES;
import static com.ainclusive.iotsim.persistence.jooq.tables.SchemaNodes.SCHEMA_NODES;
import static com.ainclusive.iotsim.persistence.jooq.tables.Schemas.SCHEMAS;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.name;
import static org.jooq.impl.DSL.table;

import com.ainclusive.iotsim.persistence.jooq.tables.records.SchemaNodesRecord;
import com.ainclusive.iotsim.persistence.jooq.tables.records.SchemasRecord;
import com.ainclusive.iotsim.platform.Ids;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.ReferenceType;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.SchemaReference;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Table;
import org.springframework.stereotype.Repository;

@Repository
public class JooqSchemaRepository implements SchemaRepository {

    private static final Field<Integer[]> ARRAY_DIMENSIONS =
            field(name("schema_nodes", "array_dimensions"), Integer[].class);
    private static final Field<String> TYPE_DEFINITION = field(name("schema_nodes", "type_definition"), String.class);
    private static final Table<?> NODE_REFERENCES = table(name("schema_node_references"));
    private static final Field<String> REFERENCE_SCHEMA_ID =
            field(name("schema_node_references", "schema_id"), String.class);
    private static final Field<String> REFERENCE_SOURCE_ID =
            field(name("schema_node_references", "source_node_id"), String.class);
    private static final Field<String> REFERENCE_TARGET_ID =
            field(name("schema_node_references", "target_node_id"), String.class);
    private static final Field<String> REFERENCE_TYPE =
            field(name("schema_node_references", "reference_type"), String.class);
    private static final Field<Boolean> REFERENCE_FORWARD =
            field(name("schema_node_references", "is_forward"), Boolean.class);

    private final DSLContext dsl;

    public JooqSchemaRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Optional<SchemaWithNodes> findCurrent(String dataSourceId) {
        String schemaId = dsl.select(DATA_SOURCES.SCHEMA_ID)
                .from(DATA_SOURCES)
                .where(DATA_SOURCES.ID.eq(dataSourceId))
                .fetchOne(DATA_SOURCES.SCHEMA_ID);
        if (schemaId == null) {
            return Optional.empty();
        }
        SchemasRecord schema = dsl.selectFrom(SCHEMAS).where(SCHEMAS.ID.eq(schemaId)).fetchOne();
        if (schema == null) {
            return Optional.empty();
        }
        Map<String, List<SchemaReference>> references = referencesBySource(schemaId);
        List<SchemaNode> nodes = dsl.selectFrom(SCHEMA_NODES)
                .where(SCHEMA_NODES.SCHEMA_ID.eq(schemaId))
                .orderBy(SCHEMA_NODES.PATH)
                .fetch()
                .map(r -> toNode(r, references));
        return Optional.of(new SchemaWithNodes(
                schema.getId(), dataSourceId, schema.getVersion(), schema.getCreatedAt(), nodes));
    }

    @Override
    public Optional<SchemaWithNodes> findByVersion(String dataSourceId, int version) {
        SchemasRecord schema = dsl.selectFrom(SCHEMAS)
                .where(SCHEMAS.DATA_SOURCE_ID.eq(dataSourceId).and(SCHEMAS.VERSION.eq(version)))
                .fetchOne();
        if (schema == null) {
            return Optional.empty();
        }
        Map<String, List<SchemaReference>> references = referencesBySource(schema.getId());
        List<SchemaNode> nodes = dsl.selectFrom(SCHEMA_NODES)
                .where(SCHEMA_NODES.SCHEMA_ID.eq(schema.getId()))
                .orderBy(SCHEMA_NODES.PATH)
                .fetch()
                .map(r -> toNode(r, references));
        return Optional.of(new SchemaWithNodes(
                schema.getId(), dataSourceId, schema.getVersion(), schema.getCreatedAt(), nodes));
    }

    @Override
    public SchemaWithNodes saveNewVersion(String dataSourceId, List<SchemaNode> nodes) {
        return dsl.transactionResult(cfg -> {
            DSLContext tx = cfg.dsl();
            Integer maxVersion = tx.select(max(SCHEMAS.VERSION))
                    .from(SCHEMAS)
                    .where(SCHEMAS.DATA_SOURCE_ID.eq(dataSourceId))
                    .fetchOne(0, Integer.class);
            int next = (maxVersion == null ? 0 : maxVersion) + 1;
            String schemaId = Ids.newId();

            SchemasRecord schema = tx.insertInto(SCHEMAS)
                    .set(SCHEMAS.ID, schemaId)
                    .set(SCHEMAS.DATA_SOURCE_ID, dataSourceId)
                    .set(SCHEMAS.VERSION, next)
                    .returning()
                    .fetchOne();

            for (SchemaNode n : nodes) {
                tx.insertInto(SCHEMA_NODES)
                        .set(SCHEMA_NODES.SCHEMA_ID, schemaId)
                        .set(SCHEMA_NODES.NODE_ID, n.nodeId())
                        .set(SCHEMA_NODES.PARENT_ID, n.parentId())
                        .set(SCHEMA_NODES.PATH, n.path())
                        .set(SCHEMA_NODES.NAME, n.name())
                        .set(SCHEMA_NODES.KIND, n.kind().name())
                        .set(SCHEMA_NODES.DATA_TYPE, n.dataType() == null ? null : n.dataType().name())
                        .set(SCHEMA_NODES.VALUE_RANK, n.valueRank() == null ? null : n.valueRank().name())
                        .set(SCHEMA_NODES.ACCESS, n.access() == null ? null : n.access().name())
                        .set(SCHEMA_NODES.UNIT, n.unit())
                        .set(SCHEMA_NODES.DESCRIPTION, n.description())
                        .set(ARRAY_DIMENSIONS, n.arrayDimensions().toArray(Integer[]::new))
                        .set(TYPE_DEFINITION, n.typeDefinition())
                        .execute();
            }
            for (SchemaNode n : nodes) {
                for (SchemaReference reference : n.references()) {
                    tx.insertInto(NODE_REFERENCES)
                            .set(REFERENCE_SCHEMA_ID, schemaId)
                            .set(REFERENCE_SOURCE_ID, n.nodeId())
                            .set(REFERENCE_TARGET_ID, reference.targetNodeId())
                            .set(REFERENCE_TYPE, reference.type().name())
                            .set(REFERENCE_FORWARD, reference.forward())
                            .execute();
                }
            }

            tx.update(DATA_SOURCES)
                    .set(DATA_SOURCES.SCHEMA_ID, schemaId)
                    .set(DATA_SOURCES.SCHEMA_VERSION, next)
                    .set(DATA_SOURCES.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                    .where(DATA_SOURCES.ID.eq(dataSourceId))
                    .execute();

            return new SchemaWithNodes(
                    schemaId, dataSourceId, next, schema.getCreatedAt(), List.copyOf(nodes));
        });
    }

    @Override
    public Map<String, Integer> countVariableNodesBySource(Collection<String> dataSourceIds) {
        if (dataSourceIds == null || dataSourceIds.isEmpty()) {
            return Map.of();
        }
        // Single query: join data_sources -> schema_nodes (via current schema_id) where kind='VARIABLE',
        // group by data_source_id. Sources with no schema or no VARIABLE nodes are absent from the result.
        Map<String, Integer> counts = new HashMap<>();
        dsl.select(DATA_SOURCES.ID, count())
                .from(DATA_SOURCES)
                .join(SCHEMA_NODES).on(SCHEMA_NODES.SCHEMA_ID.eq(DATA_SOURCES.SCHEMA_ID))
                .where(DATA_SOURCES.ID.in(dataSourceIds))
                .and(SCHEMA_NODES.KIND.eq(NodeKind.VARIABLE.name()))
                .groupBy(DATA_SOURCES.ID)
                .fetch()
                .forEach(r -> counts.put(r.value1(), r.value2()));
        // Ensure every requested id has an entry (0 if no VARIABLE nodes or no schema).
        for (String id : dataSourceIds) {
            counts.putIfAbsent(id, 0);
        }
        return counts;
    }

    private Map<String, List<SchemaReference>> referencesBySource(String schemaId) {
        Map<String, List<SchemaReference>> result = new HashMap<>();
        dsl.select(REFERENCE_SOURCE_ID, REFERENCE_TARGET_ID, REFERENCE_TYPE, REFERENCE_FORWARD)
                .from(NODE_REFERENCES).where(REFERENCE_SCHEMA_ID.eq(schemaId)).fetch().forEach(row ->
                        result.computeIfAbsent(row.get(REFERENCE_SOURCE_ID), unused -> new java.util.ArrayList<>())
                                .add(new SchemaReference(row.get(REFERENCE_TARGET_ID),
                                        ReferenceType.valueOf(row.get(REFERENCE_TYPE)), row.get(REFERENCE_FORWARD))));
        return result;
    }

    private SchemaNode toNode(SchemaNodesRecord r, Map<String, List<SchemaReference>> referencesBySource) {
        return new SchemaNode(
                r.getNodeId(),
                r.getParentId(),
                r.getPath(),
                r.getName(),
                NodeKind.valueOf(r.getKind()),
                r.getDataType() == null ? null : DataType.valueOf(r.getDataType()),
                r.getValueRank() == null ? null : ValueRank.valueOf(r.getValueRank()),
                r.getAccess() == null ? null : Access.valueOf(r.getAccess()),
                r.getUnit(),
                r.getDescription(),
                r.get(ARRAY_DIMENSIONS) == null ? List.of() : List.of(r.get(ARRAY_DIMENSIONS)),
                r.get(TYPE_DEFINITION),
                referencesBySource.getOrDefault(r.getNodeId(), List.of()));
    }
}
