package com.ainclusive.iotsim.api.security;

import java.util.EnumSet;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

/**
 * Role-based {@link PermissionService} for shared mode (IS-077).
 *
 * <p>Derives permissions from the Spring Security {@link GrantedAuthority} list already
 * present on the {@link Authentication} (populated from the JWT by the resource-server
 * chain — full OIDC/role-claim mapping lands in IS-075/IS-076). Roles are matched by
 * the {@code ROLE_} prefix convention.
 *
 * <p>Baseline role→permission mapping (backend-specs/08_AUTH_AND_MODES.md §Authorization):
 * <ul>
 *   <li>{@code admin}: all permissions.
 *   <li>{@code user}: observe + runtime-operate (start/stop), but no edits,
 *       no import/export, no admin access.
 * </ul>
 *
 * <p>This mapping is intentionally conservative: when the full flexible-permission model
 * (IS-076) lands it will replace this in-memory lookup with a DB-backed resolver without
 * changing the enforcement points ({@code @PreAuthorize} on controllers).
 *
 * <p>Active only when {@code iotsim.mode=shared}.
 */
@Service("permissionService")
@ConditionalOnProperty(name = "iotsim.mode", havingValue = "shared")
public class SharedPermissionService implements PermissionService {

    /** All permissions granted to the {@code admin} role. */
    private static final Set<Permission> ADMIN_PERMISSIONS = EnumSet.allOf(Permission.class);

    /** Permissions granted to the {@code user} role (observe + runtime-operate only). */
    private static final Set<Permission> USER_PERMISSIONS = EnumSet.of(
            Permission.OBSERVE,
            Permission.SOURCE_START,
            Permission.SOURCE_STOP,
            Permission.REPLAY_START,
            Permission.REPLAY_STOP);

    @Override
    public boolean hasPermission(Authentication authentication, Permission permission) {
        if (authentication == null || !authentication.isAuthenticated()) {
            return false;
        }
        for (GrantedAuthority authority : authentication.getAuthorities()) {
            String role = authority.getAuthority();
            if ("ROLE_admin".equals(role) && ADMIN_PERMISSIONS.contains(permission)) {
                return true;
            }
            if ("ROLE_user".equals(role) && USER_PERMISSIONS.contains(permission)) {
                return true;
            }
        }
        return false;
    }
}
