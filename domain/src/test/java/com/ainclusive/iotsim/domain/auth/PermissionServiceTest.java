package com.ainclusive.iotsim.domain.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.ainclusive.iotsim.persistence.auth.PermissionRepository;
import com.ainclusive.iotsim.persistence.auth.RoleRepository;
import com.ainclusive.iotsim.persistence.auth.UserRepository;
import com.ainclusive.iotsim.persistence.auth.UserRow;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class PermissionServiceTest {

    private RoleRepository roleRepository;
    private UserRepository userRepository;
    private PermissionRepository permissionRepository;
    private PermissionService service;

    @BeforeEach
    void setUp() {
        roleRepository = mock(RoleRepository.class);
        userRepository = mock(UserRepository.class);
        permissionRepository = mock(PermissionRepository.class);
        service = new PermissionService(roleRepository, userRepository, permissionRepository);
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    // ---- hasPermission(Permission) — security-context variant ----

    @Test
    void hasPermissionReturnsTrueWhenRoleHasIt() {
        authenticateAs("admin");
        when(roleRepository.findPermissions("admin"))
                .thenReturn(List.of(Permission.SOURCE_START.key(), Permission.ADMIN_ACCESS.key()));

        assertThat(service.hasPermission(Permission.SOURCE_START)).isTrue();
        assertThat(service.hasPermission(Permission.ADMIN_ACCESS)).isTrue();
    }

    @Test
    void hasPermissionReturnsFalseWhenRoleLacksIt() {
        authenticateAs("user");
        when(roleRepository.findPermissions("user"))
                .thenReturn(List.of(Permission.SOURCE_START.key(), Permission.PROJECT_READ.key()));

        assertThat(service.hasPermission(Permission.ADMIN_ACCESS)).isFalse();
        assertThat(service.hasPermission(Permission.PROJECT_WRITE)).isFalse();
    }

    @Test
    void hasPermissionReturnsFalseWhenNotAuthenticated() {
        // No authentication set in context.
        assertThat(service.hasPermission(Permission.SOURCE_START)).isFalse();
    }

    @Test
    void hasPermissionByKeyDelegatesToEnumLookup() {
        authenticateAs("admin");
        when(roleRepository.findPermissions("admin"))
                .thenReturn(List.of(Permission.ADMIN_ACCESS.key()));

        assertThat(service.hasPermission("admin.access")).isTrue();
        assertThat(service.hasPermission("source.start")).isFalse();
    }

    @Test
    void hasPermissionByKeyThrowsForUnknownKey() {
        authenticateAs("admin");
        assertThatThrownBy(() -> service.hasPermission("no.such.key"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void hasPermissionAggregatesAcrossMultipleRoles() {
        // A user with both "user" and a hypothetical "editor" role gets the union.
        authenticateAsRoles("user", "editor");
        when(roleRepository.findPermissions("user"))
                .thenReturn(List.of(Permission.SOURCE_START.key()));
        when(roleRepository.findPermissions("editor"))
                .thenReturn(List.of(Permission.SOURCE_WRITE.key()));

        assertThat(service.hasPermission(Permission.SOURCE_START)).isTrue();
        assertThat(service.hasPermission(Permission.SOURCE_WRITE)).isTrue();
        assertThat(service.hasPermission(Permission.ADMIN_ACCESS)).isFalse();
    }

    // ---- hasPermission(String subject, Permission) — subject-aware variant ----

    @Test
    void hasPermissionBySubjectReturnsTrueForKnownUser() {
        UserRow user = new UserRow("u1", "sub1", "Alice", "ACTIVE", null);
        when(userRepository.findBySubject("sub1")).thenReturn(Optional.of(user));
        when(userRepository.findRoles("u1")).thenReturn(List.of("admin"));
        when(roleRepository.findPermissions("admin"))
                .thenReturn(List.of(Permission.ADMIN_ACCESS.key()));

        assertThat(service.hasPermission("sub1", Permission.ADMIN_ACCESS)).isTrue();
    }

    @Test
    void hasPermissionBySubjectReturnsFalseForUnknownUser() {
        when(userRepository.findBySubject("unknown")).thenReturn(Optional.empty());
        assertThat(service.hasPermission("unknown", Permission.SOURCE_START)).isFalse();
    }

    @Test
    void hasPermissionBySubjectReturnsFalseWhenRoleLacksPermission() {
        UserRow user = new UserRow("u2", "sub2", "Bob", "ACTIVE", null);
        when(userRepository.findBySubject("sub2")).thenReturn(Optional.of(user));
        when(userRepository.findRoles("u2")).thenReturn(List.of("user"));
        when(roleRepository.findPermissions("user"))
                .thenReturn(List.of(Permission.SOURCE_START.key()));

        assertThat(service.hasPermission("sub2", Permission.ADMIN_ACCESS)).isFalse();
    }

    // ---- currentPermissions() ----

    @Test
    void currentPermissionsReturnsUnionAcrossRoles() {
        authenticateAs("admin");
        when(roleRepository.findPermissions("admin"))
                .thenReturn(List.of(
                        Permission.SOURCE_START.key(),
                        Permission.ADMIN_ACCESS.key(),
                        Permission.PROJECT_WRITE.key()));

        Set<Permission> perms = service.currentPermissions();
        assertThat(perms).containsExactlyInAnyOrder(
                Permission.SOURCE_START,
                Permission.ADMIN_ACCESS,
                Permission.PROJECT_WRITE);
    }

    @Test
    void currentPermissionsReturnsEmptyWhenNotAuthenticated() {
        assertThat(service.currentPermissions()).isEmpty();
    }

    @Test
    void currentPermissionsToleratesUnknownKeysFromFutureMigrations() {
        authenticateAs("user");
        when(roleRepository.findPermissions("user"))
                .thenReturn(List.of(Permission.SOURCE_START.key(), "future.capability"));

        // Should not throw; the unknown key is silently skipped.
        Set<Permission> perms = service.currentPermissions();
        assertThat(perms).contains(Permission.SOURCE_START);
    }

    // ---- helpers ----

    private void authenticateAs(String role) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        "principal", null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + role))));
    }

    private void authenticateAsRoles(String... roles) {
        List<SimpleGrantedAuthority> authorities = java.util.Arrays.stream(roles)
                .map(r -> new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("principal", null, authorities));
    }
}
