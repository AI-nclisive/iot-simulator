package com.ainclusive.iotsim.persistence.datasource;

import static com.ainclusive.iotsim.persistence.jooq.tables.DataSources.DATA_SOURCES;

import com.ainclusive.iotsim.persistence.jooq.tables.records.DataSourcesRecord;
import com.ainclusive.iotsim.platform.Ids;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

@Repository
public class JooqDataSourceRepository implements DataSourceRepository {

    private final DSLContext dsl;

    public JooqDataSourceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public DataSourceRow insert(String projectId, String name, String protocol, String basis,
            String endpointJson, String runtimeConfigJson, String createdBy) {
        DataSourcesRecord record = dsl.insertInto(DATA_SOURCES)
                .set(DATA_SOURCES.ID, Ids.newId())
                .set(DATA_SOURCES.PROJECT_ID, projectId)
                .set(DATA_SOURCES.NAME, name)
                .set(DATA_SOURCES.PROTOCOL, protocol)
                .set(DATA_SOURCES.BASIS, basis)
                .set(DATA_SOURCES.ENDPOINT, json(endpointJson))
                .set(DATA_SOURCES.RUNTIME_CONFIG, json(runtimeConfigJson))
                .set(DATA_SOURCES.CREATED_BY, createdBy)
                .returning()
                .fetchOne();
        return map(record);
    }

    @Override
    public List<DataSourceRow> findByProject(String projectId) {
        return dsl.selectFrom(DATA_SOURCES)
                .where(DATA_SOURCES.PROJECT_ID.eq(projectId))
                .orderBy(DATA_SOURCES.CREATED_AT.desc())
                .fetch()
                .map(this::map);
    }

    @Override
    public List<DataSourceRow> findByProjectPaged(String projectId, String protocol,
            OffsetDateTime afterAt, String afterId, int limit) {
        var q = dsl.selectFrom(DATA_SOURCES).where(DATA_SOURCES.PROJECT_ID.eq(projectId));
        if (protocol != null) {
            q = q.and(DATA_SOURCES.PROTOCOL.eq(protocol));
        }
        if (afterAt != null) {
            q = q.and(DATA_SOURCES.CREATED_AT.lt(afterAt)
                    .or(DATA_SOURCES.CREATED_AT.eq(afterAt).and(DATA_SOURCES.ID.lt(afterId))));
        }
        return q.orderBy(DATA_SOURCES.CREATED_AT.desc(), DATA_SOURCES.ID.desc())
                .limit(limit)
                .fetch()
                .map(this::map);
    }

    @Override
    public Optional<DataSourceRow> findById(String id) {
        return dsl.selectFrom(DATA_SOURCES)
                .where(DATA_SOURCES.ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    @Override
    public Optional<DataSourceRow> update(String id, String name, String endpointJson,
            String runtimeConfigJson, boolean enabled, long expectedVersion) {
        DataSourcesRecord record = dsl.update(DATA_SOURCES)
                .set(DATA_SOURCES.NAME, name)
                .set(DATA_SOURCES.ENDPOINT, json(endpointJson))
                .set(DATA_SOURCES.RUNTIME_CONFIG, json(runtimeConfigJson))
                .set(DATA_SOURCES.ENABLED, enabled)
                .set(DATA_SOURCES.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .set(DATA_SOURCES.VERSION, DATA_SOURCES.VERSION.plus(1))
                .where(DATA_SOURCES.ID.eq(id).and(DATA_SOURCES.VERSION.eq(expectedVersion)))
                .returning()
                .fetchOne();
        return Optional.ofNullable(record).map(this::map);
    }

    @Override
    public Optional<DataSourceRow> duplicate(String sourceId, String newName, String createdBy) {
        DataSourcesRecord source = dsl.selectFrom(DATA_SOURCES)
                .where(DATA_SOURCES.ID.eq(sourceId))
                .fetchOne();
        if (source == null) {
            return Optional.empty();
        }
        DataSourcesRecord copy = dsl.insertInto(DATA_SOURCES)
                .set(DATA_SOURCES.ID, Ids.newId())
                .set(DATA_SOURCES.PROJECT_ID, source.getProjectId())
                .set(DATA_SOURCES.NAME, newName)
                .set(DATA_SOURCES.PROTOCOL, source.getProtocol())
                .set(DATA_SOURCES.BASIS, source.getBasis())
                .set(DATA_SOURCES.ENDPOINT, source.getEndpoint())
                .set(DATA_SOURCES.RUNTIME_CONFIG, source.getRuntimeConfig())
                .set(DATA_SOURCES.ENABLED, false)
                .set(DATA_SOURCES.CREATED_BY, createdBy)
                .returning()
                .fetchOne();
        return Optional.ofNullable(copy).map(this::map);
    }

    @Override
    public boolean deleteById(String id) {
        return dsl.deleteFrom(DATA_SOURCES).where(DATA_SOURCES.ID.eq(id)).execute() > 0;
    }

    private static JSONB json(String value) {
        return JSONB.valueOf(value != null ? value : "{}");
    }

    private static String jsonString(JSONB value) {
        return value == null ? null : value.data();
    }

    private DataSourceRow map(DataSourcesRecord r) {
        return new DataSourceRow(
                r.getId(),
                r.getProjectId(),
                r.getName(),
                r.getProtocol(),
                r.getBasis(),
                r.getSchemaId(),
                r.getSchemaVersion(),
                jsonString(r.getEndpoint()),
                jsonString(r.getRuntimeConfig()),
                Boolean.TRUE.equals(r.getEnabled()),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getCreatedBy(),
                r.getVersion());
    }
}
