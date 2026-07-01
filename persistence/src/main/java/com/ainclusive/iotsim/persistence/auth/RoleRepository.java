package com.ainclusive.iotsim.persistence.auth;

import java.util.List;
import java.util.Optional;

/**
 * Read access over the {@code roles} and {@code role_permissions} tables.
 *
 * <p>Roles ({@code admin}, {@code user}) are seeded by V6 migration and are not
 * created at runtime in this iteration — the schema supports expansion without
 * a code change (flexible permission model, D2).
 */
public interface RoleRepository {

    List<RoleRow> findAll();

    Optional<RoleRow> findByName(String name);

    /** Returns the permission names assigned to the given role. */
    List<String> findPermissions(String roleName);

    /** Assigns a permission to a role (no-op if already assigned). */
    void assignPermission(String roleName, String permissionName);

    /** Removes a permission from a role (no-op if not assigned). */
    void removePermission(String roleName, String permissionName);
}
