package com.ainclusive.iotsim.persistence.activityevent;

import static com.ainclusive.iotsim.persistence.jooq.tables.ActivityEvents.ACTIVITY_EVENTS;

import com.ainclusive.iotsim.persistence.jooq.tables.records.ActivityEventsRecord;
import java.util.ArrayList;
import java.util.List;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.jooq.JSONB;
import org.jooq.impl.DSL;
import org.springframework.stereotype.Repository;

/** jOOQ-backed, append-only {@link ActivityEventRepository} (IS-083). */
@Repository
public class JooqActivityEventRepository implements ActivityEventRepository {

    private final DSLContext dsl;

    public JooqActivityEventRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public ActivityEventRow append(String projectId, String actor, String action,
            String objectType, String objectId, String detailJson) {
        InsertSetMoreStep<ActivityEventsRecord> insert = dsl.insertInto(ACTIVITY_EVENTS)
                .set(ACTIVITY_EVENTS.PROJECT_ID, projectId)
                .set(ACTIVITY_EVENTS.ACTOR, actor)
                .set(ACTIVITY_EVENTS.ACTION, action)
                .set(ACTIVITY_EVENTS.OBJECT_TYPE, objectType)
                .set(ACTIVITY_EVENTS.OBJECT_ID, objectId)
                .set(ACTIVITY_EVENTS.DETAIL, json(detailJson));
        return map(insert.returning().fetchOne());
    }

    @Override
    public List<ActivityEventRow> query(ActivityEventQuery filter) {
        List<Condition> conditions = new ArrayList<>();
        if (filter.projectId() != null) {
            conditions.add(ACTIVITY_EVENTS.PROJECT_ID.eq(filter.projectId()));
        }
        if (filter.actor() != null) {
            conditions.add(ACTIVITY_EVENTS.ACTOR.eq(filter.actor()));
        }
        if (filter.action() != null) {
            conditions.add(ACTIVITY_EVENTS.ACTION.eq(filter.action()));
        }
        if (filter.objectType() != null) {
            conditions.add(ACTIVITY_EVENTS.OBJECT_TYPE.eq(filter.objectType()));
        }
        if (filter.from() != null) {
            conditions.add(ACTIVITY_EVENTS.AT.ge(filter.from()));
        }
        if (filter.to() != null) {
            conditions.add(ACTIVITY_EVENTS.AT.lt(filter.to()));
        }
        // Keyset cursor: rows strictly older than (beforeAt, beforeId) in (at desc, id desc) order.
        if (filter.beforeAt() != null && filter.beforeId() != null) {
            conditions.add(DSL.row(ACTIVITY_EVENTS.AT, ACTIVITY_EVENTS.ID)
                    .lessThan(DSL.row(filter.beforeAt(), filter.beforeId())));
        }
        return dsl.selectFrom(ACTIVITY_EVENTS)
                .where(conditions)
                .orderBy(ACTIVITY_EVENTS.AT.desc(), ACTIVITY_EVENTS.ID.desc())
                .limit(filter.limit())
                .fetch()
                .map(this::map);
    }

    private static JSONB json(String value) {
        return JSONB.valueOf(value != null ? value : "{}");
    }

    private ActivityEventRow map(ActivityEventsRecord r) {
        return new ActivityEventRow(
                r.getId(),
                r.getProjectId(),
                r.getActor(),
                r.getAction(),
                r.getObjectType(),
                r.getObjectId(),
                r.getAt(),
                r.getDetail() == null ? null : r.getDetail().data());
    }
}
