package com.ainclusive.iotsim.api.security;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;

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
 * <p>The {@code sub} claim is mandatory per RFC 7519 §4.1.2. NimbusJwtDecoder does
 * not enforce it by default; a missing {@code sub} is rejected here with a 401 rather
 * than a 500.
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
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("missing_sub",
                            "JWT is missing required 'sub' claim", null));
        }
        Collection<GrantedAuthority> authorities = extractAuthorities(jwt);
        // Map.copyOf rejects null values; use a defensive copy to tolerate IdPs that
        // emit null-valued optional claims (e.g. "email": null).
        Map<String, Object> claims = nullSafeCopy(jwt.getClaims());
        IotSimPrincipal principal = new IotSimPrincipal(subject, claims, authorities);
        return new IotSimAuthentication(jwt, principal, authorities);
    }

    /**
     * Returns an unmodifiable copy of {@code source} that preserves null values.
     * {@code Map.copyOf} throws {@link NullPointerException} on null values, which
     * occurs with IdPs that emit optional claims as {@code null}.
     */
    private static Map<String, Object> nullSafeCopy(Map<String, Object> source) {
        Map<String, Object> copy = new HashMap<>(source);
        return java.util.Collections.unmodifiableMap(copy);
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
