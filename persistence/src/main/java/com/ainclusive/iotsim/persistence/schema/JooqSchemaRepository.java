package com.ainclusive.iotsim.persistence.schema;

import static com.ainclusive.iotsim.persistence.jooq.tables.DataSources.DATA_SOURCES;
import static com.ainclusive.iotsim.persistence.jooq.tables.SchemaNodes.SCHEMA_NODES;
import static com.ainclusive.iotsim.persistence.jooq.tables.Schemas.SCHEMAS;
import static org.jooq.impl.DSL.max;

import com.ainclusive.iotsim.persistence.jooq.tables.records.SchemaNodesRecord;
import com.ainclusive.iotsim.persistence.jooq.tables.records.SchemasRecord;
import com.ainclusive.iotsim.platform.Ids;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqSchemaRepository implements SchemaRepository {

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
        List<SchemaNode> nodes = dsl.selectFrom(SCHEMA_NODES)
                .where(SCHEMA_NODES.SCHEMA_ID.eq(schemaId))
                .orderBy(SCHEMA_NODES.PATH)
                .fetch()
                .map(this::toNode);
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
                        .execute();
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

    private SchemaNode toNode(SchemaNodesRecord r) {
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
                r.getDescription());
    }
}
