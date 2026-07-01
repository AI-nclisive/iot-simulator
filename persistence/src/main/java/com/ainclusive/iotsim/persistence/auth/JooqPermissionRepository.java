package com.ainclusive.iotsim.persistence.auth;

import static com.ainclusive.iotsim.persistence.jooq.tables.Permissions.PERMISSIONS;

import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqPermissionRepository implements PermissionRepository {

    private final DSLContext dsl;

    public JooqPermissionRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public PermissionRow insert(String name) {
        dsl.insertInto(PERMISSIONS)
                .set(PERMISSIONS.NAME, name)
                .onConflict(PERMISSIONS.NAME)
                .doNothing()
                .execute();
        return new PermissionRow(name);
    }

    @Override
    public Optional<PermissionRow> findByName(String name) {
        return dsl.selectFrom(PERMISSIONS)
                .where(PERMISSIONS.NAME.eq(name))
                .fetchOptional()
                .map(r -> new PermissionRow(r.getName()));
    }

    @Override
    public List<PermissionRow> findAll() {
        return dsl.selectFrom(PERMISSIONS)
                .orderBy(PERMISSIONS.NAME.asc())
                .fetch()
                .map(r -> new PermissionRow(r.getName()));
    }

    @Override
    public boolean deleteByName(String name) {
        return dsl.deleteFrom(PERMISSIONS).where(PERMISSIONS.NAME.eq(name)).execute() > 0;
    }
}
