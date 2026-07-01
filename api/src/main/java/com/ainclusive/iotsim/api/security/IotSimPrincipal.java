package com.ainclusive.iotsim.api.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authenticated identity for both deployment modes (IS-075).
 *
 * <ul>
 *   <li><b>local mode</b>: subject is {@code "local"}, claims are empty, roles are
 *       {@code ROLE_admin} + {@code ROLE_user} (full control, no OIDC round-trip).
 *   <li><b>shared mode</b>: subject is the JWT {@code sub} claim; additional claims
 *       (e.g. {@code email}, {@code roles}) are accessible via {@link #claims()};
 *       authorities carry the role-mapped grants (IS-076).
 * </ul>
 *
 * The record is intentionally lightweight — it carries only what domain code needs
 * and does not expose Spring Security internals to the domain layer.
 * See backend-specs/08_AUTH_AND_MODES.md.
 *
 * @param subject     Identity token (JWT {@code sub} in shared mode; {@code "local"}
 *                    in local mode). Never null or blank.
 * @param claims      Raw JWT claims snapshot (unmodifiable). Empty in local mode.
 * @param authorities Spring Security authorities derived from the token/role mapping.
 */
public record IotSimPrincipal(
        String subject,
        Map<String, Object> claims,
        Collection<? extends GrantedAuthority> authorities) {

    public IotSimPrincipal {
        Objects.requireNonNull(subject, "subject must not be null");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        claims = claims == null ? Map.of() : Map.copyOf(claims);
        authorities = authorities == null ? List.of() : List.copyOf(authorities);
    }

    /**
     * Returns {@code true} when this principal was created from a validated OIDC/JWT
     * token (shared mode). Returns {@code false} for the implicit local principal.
     */
    public boolean isAuthenticated() {
        return !subject.equals(LocalPrincipalFilter.LOCAL_PRINCIPAL);
    }

    /**
     * Convenience accessor: returns the value of a single JWT claim, or {@code null}
     * if the claim is absent.
     */
    public Object claim(String name) {
        return claims.get(name);
    }

    @Override
    public String toString() {
        return "IotSimPrincipal[subject=" + subject
                + ", authenticated=" + isAuthenticated() + "]";
    }
}
