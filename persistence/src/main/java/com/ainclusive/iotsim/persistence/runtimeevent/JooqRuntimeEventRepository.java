package com.ainclusive.iotsim.persistence.runtimeevent;

import static com.ainclusive.iotsim.persistence.jooq.tables.RuntimeEvents.RUNTIME_EVENTS;

import com.ainclusive.iotsim.persistence.jooq.tables.records.RuntimeEventsRecord;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

/** jOOQ-backed, append-only {@link RuntimeEventRepository} (backend-specs/04). */
@Repository
public class JooqRuntimeEventRepository implements RuntimeEventRepository {

    private final DSLContext dsl;

    public JooqRuntimeEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public RuntimeEventRow append(String projectId, String dataSourceId, String runId,
            String type, OffsetDateTime at, String payloadJson) {
        InsertSetMoreStep<RuntimeEventsRecord> insert = dsl.insertInto(RUNTIME_EVENTS)
                .set(RUNTIME_EVENTS.PROJECT_ID, projectId)
                .set(RUNTIME_EVENTS.DATA_SOURCE_ID, dataSourceId)
                .set(RUNTIME_EVENTS.RUN_ID, runId)
                .set(RUNTIME_EVENTS.TYPE, type)
                .set(RUNTIME_EVENTS.PAYLOAD, json(payloadJson));
        // A null event time falls through to the column's DB default (now()).
        if (at != null) {
            insert = insert.set(RUNTIME_EVENTS.AT, at);
        }
        return map(insert.returning().fetchOne());
    }

    @Override
    public Optional<RuntimeEventRow> findById(long id) {
        return dsl.selectFrom(RUNTIME_EVENTS)
                .where(RUNTIME_EVENTS.ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    @Override
    public List<RuntimeEventRow> findByProject(String projectId) {
        return dsl.selectFrom(RUNTIME_EVENTS)
                .where(RUNTIME_EVENTS.PROJECT_ID.eq(projectId))
                .orderBy(RUNTIME_EVENTS.AT.desc(), RUNTIME_EVENTS.ID.desc())
                .fetch()
                .map(this::map);
    }

    @Override
    public List<RuntimeEventRow> findByRun(String runId) {
        return dsl.selectFrom(RUNTIME_EVENTS)
                .where(RUNTIME_EVENTS.RUN_ID.eq(runId))
                .orderBy(RUNTIME_EVENTS.AT.desc(), RUNTIME_EVENTS.ID.desc())
                .fetch()
                .map(this::map);
    }

    @Override
    public List<RuntimeEventRow> findByDataSource(String dataSourceId) {
        return dsl.selectFrom(RUNTIME_EVENTS)
                .where(RUNTIME_EVENTS.DATA_SOURCE_ID.eq(dataSourceId))
                .orderBy(RUNTIME_EVENTS.AT.desc(), RUNTIME_EVENTS.ID.desc())
                .fetch()
                .map(this::map);
    }

    private static JSONB json(String value) {
        return JSONB.valueOf(value != null ? value : "{}");
    }

    private RuntimeEventRow map(RuntimeEventsRecord r) {
        return new RuntimeEventRow(
                r.getId(),
                r.getProjectId(),
                r.getDataSourceId(),
                r.getRunId(),
                r.getType(),
                r.getAt(),
                r.getPayload() == null ? null : r.getPayload().data());
    }
}
