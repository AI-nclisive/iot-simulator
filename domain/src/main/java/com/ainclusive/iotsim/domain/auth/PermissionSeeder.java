package com.ainclusive.iotsim.domain.auth;

import com.ainclusive.iotsim.persistence.auth.PermissionRepository;
import com.ainclusive.iotsim.persistence.auth.RoleRepository;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds the {@code permissions} table and the {@code role_permissions} mapping on startup.
 *
 * <p>The V6 migration inserts the {@code admin} and {@code user} roles but leaves the
 * {@code permissions} and {@code role_permissions} tables empty — the mapping is code-owned
 * (D2: flexible permission model) so it must stay consistent with the {@link Permission} enum.
 * This seeder runs on every startup, is fully idempotent (insert-if-absent), and seeds:
 *
 * <ul>
 *   <li>All {@link Permission} constants into the {@code permissions} table.
 *   <li>The baseline role→permission mapping:
 *     <ul>
 *       <li>{@code user}: observe + runtime operations (start/stop/configure data sources,
 *           start/stop replay and scenario runs).
 *       <li>{@code admin}: all {@code user} permissions plus edit, import/export, and admin
 *           access.
 *     </ul>
 * </ul>
 *
 * <p>Adding a new {@link Permission} constant and re-starting the application automatically
 * inserts it into the DB; no migration script is required for permission additions.
 *
 * @see Permission
 * @see PermissionService
 */
@Component
public class PermissionSeeder implements SmartInitializingSingleton {

    /** Permissions granted to the {@code user} role (observe + operate runtime). */
    static final Set<Permission> USER_PERMISSIONS = Set.of(
            Permission.PROJECT_READ,
            Permission.EVIDENCE_READ,
            Permission.SOURCE_START,
            Permission.SOURCE_STOP,
            Permission.SOURCE_CONFIGURE,
            Permission.REPLAY_START,
            Permission.REPLAY_STOP,
            Permission.SCENARIO_RUN_START,
            Permission.SCENARIO_RUN_STOP);

    /** Permissions granted to the {@code admin} role (all user permissions + edit/admin). */
    static final Set<Permission> ADMIN_PERMISSIONS;

    static {
        EnumSet<Permission> admin = EnumSet.copyOf(USER_PERMISSIONS);
        admin.add(Permission.PROJECT_WRITE);
        admin.add(Permission.SOURCE_WRITE);
        admin.add(Permission.SCHEMA_WRITE);
        admin.add(Permission.RECORDING_WRITE);
        admin.add(Permission.SCENARIO_WRITE);
        admin.add(Permission.ARTIFACT_IMPORT);
        admin.add(Permission.EVIDENCE_EXPORT);
        admin.add(Permission.ADMIN_ACCESS);
        ADMIN_PERMISSIONS = Set.copyOf(admin);
    }

    private final PermissionRepository permissionRepository;
    private final RoleRepository roleRepository;

    public PermissionSeeder(PermissionRepository permissionRepository, RoleRepository roleRepository) {
        this.permissionRepository = permissionRepository;
        this.roleRepository = roleRepository;
    }

    /**
     * Runs after all singletons are initialized ({@link SmartInitializingSingleton}).
     * Idempotent: uses insert-on-conflict-do-nothing semantics so multiple restarts are safe.
     */
    @Override
    @Transactional
    public void afterSingletonsInstantiated() {
        seed();
    }

    /**
     * Seeds permissions and role→permission mappings. Package-visible for testing.
     */
    @Transactional
    void seed() {
        // Ensure every known permission exists in the DB.
        for (Permission p : Permission.values()) {
            permissionRepository.insert(p.key());
        }

        // Map permissions to roles.
        for (Permission p : USER_PERMISSIONS) {
            roleRepository.assignPermission("user", p.key());
        }
        for (Permission p : ADMIN_PERMISSIONS) {
            roleRepository.assignPermission("admin", p.key());
        }
    }
}
