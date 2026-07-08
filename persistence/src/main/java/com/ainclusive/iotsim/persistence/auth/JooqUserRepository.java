package com.ainclusive.iotsim.persistence.auth;

import static com.ainclusive.iotsim.persistence.jooq.tables.UserRoles.USER_ROLES;
import static com.ainclusive.iotsim.persistence.jooq.tables.Users.USERS;

import com.ainclusive.iotsim.persistence.jooq.tables.records.UsersRecord;
import com.ainclusive.iotsim.platform.Ids;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqUserRepository implements UserRepository {

    private final DSLContext dsl;

    public JooqUserRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public UserRow upsert(String subject, String displayName) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        UsersRecord record = dsl.insertInto(USERS)
                .set(USERS.ID, Ids.newId())
                .set(USERS.SUBJECT, subject)
                .set(USERS.DISPLAY_NAME, displayName)
                .set(USERS.LAST_SEEN_AT, now)
                .onConflict(USERS.SUBJECT)
                .doUpdate()
                .set(USERS.DISPLAY_NAME, displayName)
                .set(USERS.LAST_SEEN_AT, now)
                .returning()
                .fetchOne();
        return map(record);
    }

    @Override
    public Optional<UserRow> findById(String id) {
        return dsl.selectFrom(USERS)
                .where(USERS.ID.eq(id))
                .fetchOptional()
                .map(this::map);
    }

    @Override
    public Optional<UserRow> findBySubject(String subject) {
        return dsl.selectFrom(USERS)
                .where(USERS.SUBJECT.eq(subject))
                .fetchOptional()
                .map(this::map);
    }

    @Override
    public List<UserRow> findAll() {
        return dsl.selectFrom(USERS)
                .orderBy(USERS.ID.asc())
                .fetch()
                .map(this::map);
    }

    @Override
    public Optional<UserRow> updateStatus(String id, String status) {
        UsersRecord record = dsl.update(USERS)
                .set(USERS.STATUS, status)
                .where(USERS.ID.eq(id))
                .returning()
                .fetchOne();
        return Optional.ofNullable(record).map(this::map);
    }

    @Override
    public void assignRole(String userId, String roleName) {
        dsl.insertInto(USER_ROLES)
                .set(USER_ROLES.USER_ID, userId)
                .set(USER_ROLES.ROLE_NAME, roleName)
                .onConflict(USER_ROLES.USER_ID, USER_ROLES.ROLE_NAME)
                .doNothing()
                .execute();
    }

    @Override
    public void removeRole(String userId, String roleName) {
        dsl.deleteFrom(USER_ROLES)
                .where(USER_ROLES.USER_ID.eq(userId).and(USER_ROLES.ROLE_NAME.eq(roleName)))
                .execute();
    }

    @Override
    public List<String> findRoles(String userId) {
        return dsl.select(USER_ROLES.ROLE_NAME)
                .from(USER_ROLES)
                .where(USER_ROLES.USER_ID.eq(userId))
                .fetch(USER_ROLES.ROLE_NAME);
    }

    @Override
    public Map<String, List<String>> findAllRoles() {
        return dsl.select(USER_ROLES.USER_ID, USER_ROLES.ROLE_NAME)
                .from(USER_ROLES)
                .fetchGroups(USER_ROLES.USER_ID, USER_ROLES.ROLE_NAME);
    }

    @Override
    public boolean deleteById(String id) {
        return dsl.deleteFrom(USERS).where(USERS.ID.eq(id)).execute() > 0;
    }

    private UserRow map(UsersRecord r) {
        return new UserRow(
                r.getId(),
                r.getSubject(),
                r.getDisplayName(),
                r.getStatus(),
                r.getLastSeenAt());
    }
}
