package com.ainclusive.iotsim.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PermissionSeederTest {

    @Test
    void adminPermissionsContainAllUserPermissions() {
        assertThat(PermissionSeeder.ADMIN_PERMISSIONS)
                .containsAll(PermissionSeeder.USER_PERMISSIONS);
    }

    @Test
    void adminPermissionsContainAdminAccess() {
        assertThat(PermissionSeeder.ADMIN_PERMISSIONS).contains(Permission.ADMIN_ACCESS);
    }

    @Test
    void userPermissionsDoNotContainAdminAccess() {
        assertThat(PermissionSeeder.USER_PERMISSIONS).doesNotContain(Permission.ADMIN_ACCESS);
    }

    @Test
    void userPermissionsDoNotContainWriteOrExportPermissions() {
        // user role can observe and operate, but not edit or import/export
        Set<Permission> editOrExport = EnumSet.of(
                Permission.PROJECT_WRITE,
                Permission.SOURCE_WRITE,
                Permission.SCHEMA_WRITE,
                Permission.RECORDING_WRITE,
                Permission.SCENARIO_WRITE,
                Permission.ARTIFACT_IMPORT,
                Permission.EVIDENCE_EXPORT,
                Permission.ADMIN_ACCESS);
        for (Permission p : editOrExport) {
            assertThat(PermissionSeeder.USER_PERMISSIONS)
                    .as("user role must not have %s", p)
                    .doesNotContain(p);
        }
    }

    @Test
    void userPermissionsIncludeRuntimeOperations() {
        assertThat(PermissionSeeder.USER_PERMISSIONS).containsAll(Set.of(
                Permission.PROJECT_READ,
                Permission.EVIDENCE_READ,
                Permission.SOURCE_START,
                Permission.SOURCE_STOP,
                Permission.SOURCE_CONFIGURE,
                Permission.REPLAY_START,
                Permission.REPLAY_STOP,
                Permission.SCENARIO_RUN_START,
                Permission.SCENARIO_RUN_STOP));
    }

    @Test
    void adminPermissionsContainEditAndImportExport() {
        assertThat(PermissionSeeder.ADMIN_PERMISSIONS).containsAll(Set.of(
                Permission.PROJECT_WRITE,
                Permission.SOURCE_WRITE,
                Permission.SCHEMA_WRITE,
                Permission.RECORDING_WRITE,
                Permission.SCENARIO_WRITE,
                Permission.ARTIFACT_IMPORT,
                Permission.EVIDENCE_EXPORT));
    }

    @Test
    void seedIsIdempotent() {
        // Calling seed() twice on a fake repository must not produce duplicate entries.
        FakePermissionRepository perms = new FakePermissionRepository();
        FakeRoleRepository roles = new FakeRoleRepository();
        PermissionSeeder seeder = new PermissionSeeder(perms, roles);

        seeder.seed();
        seeder.seed();

        // All Permission enum values inserted, no duplicates.
        assertThat(perms.inserted).containsExactlyInAnyOrderElementsOf(
                java.util.Arrays.stream(Permission.values())
                        .map(Permission::key)
                        .toList());

        // Role mappings are idempotent too (the fake uses a Set).
        for (Permission p : PermissionSeeder.USER_PERMISSIONS) {
            assertThat(roles.userPermissions).contains(p.key());
        }
        for (Permission p : PermissionSeeder.ADMIN_PERMISSIONS) {
            assertThat(roles.adminPermissions).contains(p.key());
        }
    }

    // ---- fakes ----

    private static class FakePermissionRepository
            implements com.ainclusive.iotsim.persistence.auth.PermissionRepository {
        final Set<String> inserted = new java.util.LinkedHashSet<>();

        @Override
        public com.ainclusive.iotsim.persistence.auth.PermissionRow insert(String name) {
            inserted.add(name);
            return new com.ainclusive.iotsim.persistence.auth.PermissionRow(name);
        }

        @Override
        public java.util.Optional<com.ainclusive.iotsim.persistence.auth.PermissionRow> findByName(String name) {
            return inserted.contains(name)
                    ? java.util.Optional.of(new com.ainclusive.iotsim.persistence.auth.PermissionRow(name))
                    : java.util.Optional.empty();
        }

        @Override
        public java.util.List<com.ainclusive.iotsim.persistence.auth.PermissionRow> findAll() {
            return inserted.stream()
                    .map(com.ainclusive.iotsim.persistence.auth.PermissionRow::new)
                    .toList();
        }

        @Override
        public boolean deleteByName(String name) {
            return inserted.remove(name);
        }
    }

    private static class FakeRoleRepository
            implements com.ainclusive.iotsim.persistence.auth.RoleRepository {
        final Set<String> userPermissions = new java.util.LinkedHashSet<>();
        final Set<String> adminPermissions = new java.util.LinkedHashSet<>();

        @Override
        public java.util.List<com.ainclusive.iotsim.persistence.auth.RoleRow> findAll() {
            return java.util.List.of(
                    new com.ainclusive.iotsim.persistence.auth.RoleRow("admin"),
                    new com.ainclusive.iotsim.persistence.auth.RoleRow("user"));
        }

        @Override
        public java.util.Optional<com.ainclusive.iotsim.persistence.auth.RoleRow> findByName(String name) {
            return java.util.Optional.of(new com.ainclusive.iotsim.persistence.auth.RoleRow(name));
        }

        @Override
        public java.util.List<String> findPermissions(String roleName) {
            return "admin".equals(roleName) ? new java.util.ArrayList<>(adminPermissions)
                    : new java.util.ArrayList<>(userPermissions);
        }

        @Override
        public void assignPermission(String roleName, String permissionName) {
            if ("admin".equals(roleName)) {
                adminPermissions.add(permissionName);
            } else {
                userPermissions.add(permissionName);
            }
        }

        @Override
        public void removePermission(String roleName, String permissionName) {
            if ("admin".equals(roleName)) {
                adminPermissions.remove(permissionName);
            } else {
                userPermissions.remove(permissionName);
            }
        }
    }
}
