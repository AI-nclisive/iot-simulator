package com.ainclusive.iotsim.persistence.auth;

import static com.ainclusive.iotsim.persistence.jooq.tables.RolePermissions.ROLE_PERMISSIONS;
import static com.ainclusive.iotsim.persistence.jooq.tables.Roles.ROLES;

import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqRoleRepository implements RoleRepository {

    private final DSLContext dsl;

    public JooqRoleRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public List<RoleRow> findAll() {
        return dsl.selectFrom(ROLES)
                .orderBy(ROLES.NAME.asc())
                .fetch()
                .map(r -> new RoleRow(r.getName()));
    }

    @Override
    public Optional<RoleRow> findByName(String name) {
        return dsl.selectFrom(ROLES)
                .where(ROLES.NAME.eq(name))
                .fetchOptional()
                .map(r -> new RoleRow(r.getName()));
    }

    @Override
    public List<String> findPermissions(String roleName) {
        return dsl.select(ROLE_PERMISSIONS.PERMISSION_NAME)
                .from(ROLE_PERMISSIONS)
                .where(ROLE_PERMISSIONS.ROLE_NAME.eq(roleName))
                .fetch(ROLE_PERMISSIONS.PERMISSION_NAME);
    }

    @Override
    public void assignPermission(String roleName, String permissionName) {
        dsl.insertInto(ROLE_PERMISSIONS)
                .set(ROLE_PERMISSIONS.ROLE_NAME, roleName)
                .set(ROLE_PERMISSIONS.PERMISSION_NAME, permissionName)
                .onConflict(ROLE_PERMISSIONS.ROLE_NAME, ROLE_PERMISSIONS.PERMISSION_NAME)
                .doNothing()
                .execute();
    }

    @Override
    public void removePermission(String roleName, String permissionName) {
        dsl.deleteFrom(ROLE_PERMISSIONS)
                .where(ROLE_PERMISSIONS.ROLE_NAME.eq(roleName)
                        .and(ROLE_PERMISSIONS.PERMISSION_NAME.eq(permissionName)))
                .execute();
    }
}
