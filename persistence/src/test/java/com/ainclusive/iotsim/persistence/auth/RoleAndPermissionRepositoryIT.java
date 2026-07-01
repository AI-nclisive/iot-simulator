package com.ainclusive.iotsim.persistence.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Exercises RoleRepository and PermissionRepository (roles, permissions, role_permissions)
 * against a real Postgres. Skipped gracefully when no Docker daemon is available.
 */
@Testcontainers(disabledWithoutDocker = true)
class RoleAndPermissionRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static RoleRepository roleRepository;
    static PermissionRepository permissionRepository;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DSLContext dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        roleRepository = new JooqRoleRepository(dsl);
        permissionRepository = new JooqPermissionRepository(dsl);
    }

    @Test
    void seededRolesExist() {
        List<RoleRow> roles = roleRepository.findAll();
        assertThat(roles).extracting(RoleRow::name).containsExactlyInAnyOrder("admin", "user");
    }

    @Test
    void findRoleByName() {
        assertThat(roleRepository.findByName("admin")).isPresent();
        assertThat(roleRepository.findByName("nobody")).isEmpty();
    }

    @Test
    void insertAndFindPermission() {
        permissionRepository.insert("source.start");
        assertThat(permissionRepository.findByName("source.start")).isPresent();
        assertThat(permissionRepository.findByName("does.not.exist")).isEmpty();
    }

    @Test
    void insertPermissionIsIdempotent() {
        permissionRepository.insert("source.stop");
        permissionRepository.insert("source.stop"); // no exception
        assertThat(permissionRepository.findAll()).extracting(PermissionRow::name).contains("source.stop");
    }

    @Test
    void assignAndRemoveRolePermission() {
        permissionRepository.insert("admin.access");
        roleRepository.assignPermission("admin", "admin.access");
        assertThat(roleRepository.findPermissions("admin")).contains("admin.access");

        // idempotent
        roleRepository.assignPermission("admin", "admin.access");
        long count = roleRepository.findPermissions("admin").stream()
                .filter(p -> p.equals("admin.access"))
                .count();
        assertThat(count).isEqualTo(1);

        roleRepository.removePermission("admin", "admin.access");
        assertThat(roleRepository.findPermissions("admin")).doesNotContain("admin.access");

        // removing non-assigned permission is a no-op
        roleRepository.removePermission("admin", "admin.access");
    }

    @Test
    void deletePermission() {
        permissionRepository.insert("to.delete");
        assertThat(permissionRepository.deleteByName("to.delete")).isTrue();
        assertThat(permissionRepository.findByName("to.delete")).isEmpty();
        assertThat(permissionRepository.deleteByName("to.delete")).isFalse();
    }
}
