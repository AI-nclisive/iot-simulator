package com.ainclusive.iotsim.persistence.manualschema;

import static com.ainclusive.iotsim.persistence.jooq.tables.ManualSchemas.MANUAL_SCHEMAS;

import com.ainclusive.iotsim.persistence.jooq.tables.records.ManualSchemasRecord;
import com.ainclusive.iotsim.platform.Ids;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

@Repository
public class JooqManualSchemaRepository implements ManualSchemaRepository {

    private final DSLContext dsl;

    public JooqManualSchemaRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public ManualSchemaRow create(String projectId, String protocol, String name, String description,
            String nodesJson, String createdBy) {
        ManualSchemasRecord record = dsl.insertInto(MANUAL_SCHEMAS)
                .set(MANUAL_SCHEMAS.ID, Ids.newId())
                .set(MANUAL_SCHEMAS.PROJECT_ID, projectId)
                .set(MANUAL_SCHEMAS.PROTOCOL, protocol)
                .set(MANUAL_SCHEMAS.NAME, name)
                .set(MANUAL_SCHEMAS.DESCRIPTION, description)
                .set(MANUAL_SCHEMAS.NODES, json(nodesJson))
                .set(MANUAL_SCHEMAS.CREATED_BY, createdBy)
                .returning()
                .fetchOne();
        return map(record);
    }

    @Override
    public List<ManualSchemaRow> findByProject(String projectId) {
        return dsl.selectFrom(MANUAL_SCHEMAS)
                .where(MANUAL_SCHEMAS.PROJECT_ID.eq(projectId))
                .orderBy(MANUAL_SCHEMAS.CREATED_AT.desc())
                .fetch()
                .map(this::map);
    }

    @Override
    public List<ManualSchemaRow> findByProjectPaged(String projectId,
            OffsetDateTime afterAt, String afterId, int limit) {
        var q = dsl.selectFrom(MANUAL_SCHEMAS).where(MANUAL_SCHEMAS.PROJECT_ID.eq(projectId));
        if (afterAt != null) {
            q = q.and(MANUAL_SCHEMAS.CREATED_AT.lt(afterAt)
                    .or(MANUAL_SCHEMAS.CREATED_AT.eq(afterAt).and(MANUAL_SCHEMAS.ID.lt(afterId))));
        }
        return q.orderBy(MANUAL_SCHEMAS.CREATED_AT.desc(), MANUAL_SCHEMAS.ID.desc())
                .limit(limit)
                .fetch()
                .map(this::map);
    }

    @Override
    public Optional<ManualSchemaRow> findById(String id) {
        return dsl.selectFrom(MANUAL_SCHEMAS).where(MANUAL_SCHEMAS.ID.eq(id)).fetchOptional().map(this::map);
    }

    @Override
    public Optional<ManualSchemaRow> update(String id, String name, String description,
            String nodesJson, long expectedVersion) {
        ManualSchemasRecord record = dsl.update(MANUAL_SCHEMAS)
                .set(MANUAL_SCHEMAS.NAME, name)
                .set(MANUAL_SCHEMAS.DESCRIPTION, description)
                .set(MANUAL_SCHEMAS.NODES, json(nodesJson))
                .set(MANUAL_SCHEMAS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .set(MANUAL_SCHEMAS.VERSION, MANUAL_SCHEMAS.VERSION.plus(1))
                .where(MANUAL_SCHEMAS.ID.eq(id).and(MANUAL_SCHEMAS.VERSION.eq(expectedVersion)))
                .returning()
                .fetchOne();
        return Optional.ofNullable(record).map(this::map);
    }

    @Override
    public Optional<ManualSchemaRow> duplicate(String sourceId, String newName, String createdBy) {
        ManualSchemasRecord source = dsl.selectFrom(MANUAL_SCHEMAS)
                .where(MANUAL_SCHEMAS.ID.eq(sourceId))
                .fetchOne();
        if (source == null) {
            return Optional.empty();
        }
        ManualSchemasRecord copy = dsl.insertInto(MANUAL_SCHEMAS)
                .set(MANUAL_SCHEMAS.ID, Ids.newId())
                .set(MANUAL_SCHEMAS.PROJECT_ID, source.getProjectId())
                .set(MANUAL_SCHEMAS.PROTOCOL, source.getProtocol())
                .set(MANUAL_SCHEMAS.NAME, newName)
                .set(MANUAL_SCHEMAS.DESCRIPTION, source.getDescription())
                .set(MANUAL_SCHEMAS.NODES, source.getNodes())
                .set(MANUAL_SCHEMAS.CREATED_BY, createdBy)
                .returning()
                .fetchOne();
        return Optional.ofNullable(copy).map(this::map);
    }

    @Override
    public boolean deleteById(String id) {
        return dsl.deleteFrom(MANUAL_SCHEMAS).where(MANUAL_SCHEMAS.ID.eq(id)).execute() > 0;
    }

    private static JSONB json(String value) {
        return JSONB.valueOf(value != null && !value.isBlank() ? value : "[]");
    }

    private ManualSchemaRow map(ManualSchemasRecord r) {
        return new ManualSchemaRow(
                r.getId(),
                r.getProjectId(),
                r.getProtocol(),
                r.getName(),
                r.getDescription(),
                r.getNodes() == null ? "[]" : r.getNodes().data(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getCreatedBy(),
                r.getVersion());
    }
}
