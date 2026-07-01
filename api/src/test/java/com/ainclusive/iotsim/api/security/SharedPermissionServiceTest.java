package com.ainclusive.iotsim.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Unit tests for {@link SharedPermissionService} — verifies role→permission mapping
 * without Spring context. IS-077.
 */
class SharedPermissionServiceTest {

    private final SharedPermissionService service = new SharedPermissionService();

    private static Authentication auth(String... roles) {
        List<SimpleGrantedAuthority> authorities =
                List.of(roles).stream().map(SimpleGrantedAuthority::new).toList();
        return new UsernamePasswordAuthenticationToken("user", null, authorities);
    }

    // ── admin has all permissions ─────────────────────────────────────────────

    @Test
    void adminHasObserve() {
        assertThat(service.hasPermission(auth("ROLE_admin"), Permission.OBSERVE)).isTrue();
    }

    @Test
    void adminHasProjectEdit() {
        assertThat(service.hasPermission(auth("ROLE_admin"), Permission.PROJECT_EDIT)).isTrue();
    }

    @Test
    void adminHasSourceEdit() {
        assertThat(service.hasPermission(auth("ROLE_admin"), Permission.SOURCE_EDIT)).isTrue();
    }

    @Test
    void adminHasSchemaEdit() {
        assertThat(service.hasPermission(auth("ROLE_admin"), Permission.SCHEMA_EDIT)).isTrue();
    }

    @Test
    void adminHasScenarioEdit() {
        assertThat(service.hasPermission(auth("ROLE_admin"), Permission.SCENARIO_EDIT)).isTrue();
    }

    @Test
    void adminHasImportExport() {
        assertThat(service.hasPermission(auth("ROLE_admin"), Permission.IMPORT_EXPORT)).isTrue();
    }

    @Test
    void adminHasAdminAccess() {
        assertThat(service.hasPermission(auth("ROLE_admin"), Permission.ADMIN_ACCESS)).isTrue();
    }

    @Test
    void adminHasSourceStart() {
        assertThat(service.hasPermission(auth("ROLE_admin"), Permission.SOURCE_START)).isTrue();
    }

    // ── user has observe + runtime-operate only ───────────────────────────────

    @Test
    void userHasObserve() {
        assertThat(service.hasPermission(auth("ROLE_user"), Permission.OBSERVE)).isTrue();
    }

    @Test
    void userHasSourceStart() {
        assertThat(service.hasPermission(auth("ROLE_user"), Permission.SOURCE_START)).isTrue();
    }

    @Test
    void userHasSourceStop() {
        assertThat(service.hasPermission(auth("ROLE_user"), Permission.SOURCE_STOP)).isTrue();
    }

    @Test
    void userHasReplayStart() {
        assertThat(service.hasPermission(auth("ROLE_user"), Permission.REPLAY_START)).isTrue();
    }

    @Test
    void userHasReplayStop() {
        assertThat(service.hasPermission(auth("ROLE_user"), Permission.REPLAY_STOP)).isTrue();
    }

    @Test
    void userCannotEditProject() {
        assertThat(service.hasPermission(auth("ROLE_user"), Permission.PROJECT_EDIT)).isFalse();
    }

    @Test
    void userCannotEditSource() {
        assertThat(service.hasPermission(auth("ROLE_user"), Permission.SOURCE_EDIT)).isFalse();
    }

    @Test
    void userCannotEditSchema() {
        assertThat(service.hasPermission(auth("ROLE_user"), Permission.SCHEMA_EDIT)).isFalse();
    }

    @Test
    void userCannotImportExport() {
        assertThat(service.hasPermission(auth("ROLE_user"), Permission.IMPORT_EXPORT)).isFalse();
    }

    @Test
    void userCannotAccessAdmin() {
        assertThat(service.hasPermission(auth("ROLE_user"), Permission.ADMIN_ACCESS)).isFalse();
    }

    // ── unknown roles / unauthenticated ──────────────────────────────────────

    @Test
    void unknownRoleHasNoPermissions() {
        assertThat(service.hasPermission(auth("ROLE_viewer"), Permission.OBSERVE)).isFalse();
    }

    @Test
    void nullAuthenticationReturnsFalse() {
        assertThat(service.hasPermission(null, Permission.OBSERVE)).isFalse();
    }
}
