package com.ainclusive.iotsim.api.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Converts a validated Spring Security {@link Jwt} into an
 * {@link AbstractAuthenticationToken} whose principal is an {@link IotSimPrincipal}
 * (IS-075).
 *
 * <p>Role mapping (IS-076): the configurable claim named {@link #rolesClaim} (default
 * {@code "roles"}) is expected to carry a {@code List<String>}; each value is
 * prefixed with {@code "ROLE_"} and added as a {@link GrantedAuthority}. Unknown or
 * absent claims produce an empty authority list — the request is authenticated but
 * carries no product roles, which IS-077 will enforce.
 *
 * <p>The {@code sub} claim is mandatory per RFC 7519 §4.1.2. Tokens without a
 * subject are rejected upstream by Spring's JWT decoder.
 *
 * <p>See backend-specs/08_AUTH_AND_MODES.md.
 */
public class JwtPrincipalConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /**
     * Default JWT claim used to extract product roles.  Configurable via
     * {@link OidcProperties#rolesClaim()} so operators can point to {@code "groups"}
     * or any other IdP-specific claim without recompiling.
     */
    public static final String DEFAULT_ROLES_CLAIM = "roles";

    private final String rolesClaim;

    public JwtPrincipalConverter() {
        this(DEFAULT_ROLES_CLAIM);
    }

    public JwtPrincipalConverter(String rolesClaim) {
        this.rolesClaim =
                (rolesClaim == null || rolesClaim.isBlank()) ? DEFAULT_ROLES_CLAIM : rolesClaim;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        IotSimPrincipal principal = new IotSimPrincipal(jwt.getSubject(),
                Map.copyOf(jwt.getClaims()), authorities);
        return new JwtAuthenticationToken(jwt, authorities, principal.subject());
    }

    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        Object rawRoles = jwt.getClaims().get(rolesClaim);
        if (!(rawRoles instanceof List<?> roleList)) {
            return List.of();
        }
        return roleList.stream()
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .filter(r -> !r.isBlank())
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .toList();
    }
}
