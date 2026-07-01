package com.ainclusive.iotsim.domain.auth;

import com.ainclusive.iotsim.persistence.auth.PermissionRepository;
import com.ainclusive.iotsim.persistence.auth.RoleRepository;
import com.ainclusive.iotsim.persistence.auth.UserRepository;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Domain service for the flexible permission model (IS-076 / D2).
 *
 * <p>This service answers the central authorization question:
 * <em>"does the authenticated principal hold a given permission?"</em>
 *
 * <p>How it works:
 * <ol>
 *   <li>In <b>local trusted mode</b> the implicit {@code local} principal is granted the full
 *       {@code admin} permission set at the filter level (see {@code LocalPrincipalFilter}).
 *       {@link #hasPermission(Permission)} therefore returns {@code true} for every permission.
 *   <li>In <b>shared mode</b> a bearer JWT carries role claims; Spring Security translates them to
 *       {@code ROLE_<roleName>} {@link GrantedAuthority} entries. This service resolves the
 *       matching DB permissions and checks membership.
 * </ol>
 *
 * <p>Enforcement is in the API layer — this service is the single evaluation point so that
 * enforcement code never has to enumerate roles directly (D2 contract: "same permission checks
 * stay" when the role set expands).
 *
 * @see PermissionSeeder
 * @see Permission
 */
@Service
public class PermissionService {

    /**
     * Bare role name granted to the implicit {@code local} principal; it receives every permission
     * unconditionally without hitting the DB (local mode is auth-off).
     *
     * <p>Named {@code ADMIN_ROLE_NAME} (not {@code ROLE_ADMIN}) to avoid confusion with Spring
     * Security's {@code ROLE_X} authority-string convention — this value is the bare name
     * {@code "admin"}, not the prefixed authority {@code "ROLE_admin"}.
     */
    public static final String ADMIN_ROLE_NAME = "admin";

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PermissionRepository permissionRepository;

    public PermissionService(
            RoleRepository roleRepository,
            UserRepository userRepository,
            PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.permissionRepository = permissionRepository;
    }

    /**
     * Returns {@code true} when the currently authenticated principal holds the given permission.
     *
     * <p>Resolution:
     * <ol>
     *   <li>The principal's granted authorities are read from the {@link SecurityContextHolder}.
     *       In local mode this is {@code [ROLE_admin, ROLE_user]}; in shared mode it is
     *       derived from the JWT.
     *   <li>For each {@code ROLE_<name>} authority the DB is queried for the permission set.
     *   <li>The union is checked for membership.
     * </ol>
     *
     * @param permission the capability to check
     * @return {@code true} iff the principal holds this permission
     */
    public boolean hasPermission(Permission permission) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        Set<String> roleNames = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .collect(Collectors.toSet());

        return roleNames.stream()
                .anyMatch(role -> roleRepository.findPermissions(role).contains(permission.key()));
    }

    /**
     * Convenience overload that accepts a raw permission key string (e.g. from annotation values).
     *
     * @param permissionKey dot-scoped key, e.g. {@code "source.start"}
     * @return {@code true} iff the principal holds this permission
     * @throws IllegalArgumentException if the key does not map to a known {@link Permission}
     */
    public boolean hasPermission(String permissionKey) {
        return hasPermission(Permission.fromKey(permissionKey));
    }

    /**
     * Returns {@code true} when the principal identified by {@code subject} holds the given
     * permission. This is the user-aware variant used when the caller already has a resolved
     * user subject (e.g. from a JWT claim), rather than reading from the security context.
     *
     * @param subject    OIDC subject of the user
     * @param permission the capability to check
     * @return {@code true} iff the user holds this permission
     */
    public boolean hasPermission(String subject, Permission permission) {
        return userRepository.findBySubject(subject)
                .map(user -> {
                    List<String> roleNames = userRepository.findRoles(user.id());
                    return roleNames.stream()
                            .anyMatch(role ->
                                    roleRepository.findPermissions(role).contains(permission.key()));
                })
                .orElse(false);
    }

    /**
     * Returns the full set of {@link Permission}s held by the currently authenticated principal.
     * Useful for building capability-based responses (e.g. "which actions is this user allowed?").
     *
     * @return unmodifiable set of permissions; empty when the principal has no roles in the DB
     */
    public Set<Permission> currentPermissions() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Set.of();
        }
        Set<String> roleNames = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .collect(Collectors.toSet());

        EnumSet<Permission> result = EnumSet.noneOf(Permission.class);
        for (String role : roleNames) {
            for (String key : roleRepository.findPermissions(role)) {
                try {
                    result.add(Permission.fromKey(key));
                } catch (IllegalArgumentException ignored) {
                    // Unknown keys from future migrations are tolerated.
                }
            }
        }
        return Set.copyOf(result);
    }
}
