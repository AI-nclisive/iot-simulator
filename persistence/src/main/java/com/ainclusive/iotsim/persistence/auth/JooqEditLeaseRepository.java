package com.ainclusive.iotsim.persistence.auth;

import static com.ainclusive.iotsim.persistence.jooq.tables.EditLeases.EDIT_LEASES;

import com.ainclusive.iotsim.persistence.jooq.tables.records.EditLeasesRecord;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqEditLeaseRepository implements EditLeaseRepository {

    private final DSLContext dsl;

    public JooqEditLeaseRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public EditLeaseRow acquireOrRenew(String objectType, String objectId, String holder, Duration ttl) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        OffsetDateTime expiresAt = now.plus(ttl);

        EditLeasesRecord record = dsl.insertInto(EDIT_LEASES)
                .set(EDIT_LEASES.OBJECT_TYPE, objectType)
                .set(EDIT_LEASES.OBJECT_ID, objectId)
                .set(EDIT_LEASES.HOLDER, holder)
                .set(EDIT_LEASES.ACQUIRED_AT, now)
                .set(EDIT_LEASES.EXPIRES_AT, expiresAt)
                .onConflict(EDIT_LEASES.OBJECT_TYPE, EDIT_LEASES.OBJECT_ID)
                .doUpdate()
                // Only renew when the existing lease belongs to this holder OR has expired
                .set(EDIT_LEASES.HOLDER, org.jooq.impl.DSL.when(
                        EDIT_LEASES.HOLDER.eq(holder).or(EDIT_LEASES.EXPIRES_AT.lt(now)),
                        holder).otherwise(EDIT_LEASES.HOLDER))
                .set(EDIT_LEASES.EXPIRES_AT, org.jooq.impl.DSL.when(
                        EDIT_LEASES.HOLDER.eq(holder).or(EDIT_LEASES.EXPIRES_AT.lt(now)),
                        expiresAt).otherwise(EDIT_LEASES.EXPIRES_AT))
                .returning()
                .fetchOne();
        return map(record);
    }

    @Override
    public Optional<EditLeaseRow> findActive(String objectType, String objectId) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return dsl.selectFrom(EDIT_LEASES)
                .where(EDIT_LEASES.OBJECT_TYPE.eq(objectType)
                        .and(EDIT_LEASES.OBJECT_ID.eq(objectId))
                        .and(EDIT_LEASES.EXPIRES_AT.gt(now)))
                .fetchOptional()
                .map(this::map);
    }

    @Override
    public List<EditLeaseRow> findAllActiveByHolder(String holder) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return dsl.selectFrom(EDIT_LEASES)
                .where(EDIT_LEASES.HOLDER.eq(holder)
                        .and(EDIT_LEASES.EXPIRES_AT.gt(now)))
                .orderBy(EDIT_LEASES.ACQUIRED_AT.asc())
                .fetch()
                .map(this::map);
    }

    @Override
    public boolean release(String objectType, String objectId, String holder) {
        return dsl.deleteFrom(EDIT_LEASES)
                .where(EDIT_LEASES.OBJECT_TYPE.eq(objectType)
                        .and(EDIT_LEASES.OBJECT_ID.eq(objectId))
                        .and(EDIT_LEASES.HOLDER.eq(holder)))
                .execute() > 0;
    }

    @Override
    public int deleteExpired() {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return dsl.deleteFrom(EDIT_LEASES)
                .where(EDIT_LEASES.EXPIRES_AT.le(now))
                .execute();
    }

    private EditLeaseRow map(EditLeasesRecord r) {
        return new EditLeaseRow(
                r.getObjectType(),
                r.getObjectId(),
                r.getHolder(),
                r.getAcquiredAt(),
                r.getExpiresAt());
    }
}
