package com.ainclusive.iotsim.api.security;

import java.util.Collection;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * {@link AbstractAuthenticationToken} whose {@link #getPrincipal()} returns
 * an {@link IotSimPrincipal} (IS-075).
 *
 * <p>Spring Security's built-in {@code JwtAuthenticationToken} stores the raw
 * {@code Jwt} as its principal.  Downstream code (IS-076/IS-077) needs to cast
 * the principal to {@link IotSimPrincipal}, so this wrapper keeps the raw
 * {@link Jwt} as credentials while exposing the converted principal.
 */
public final class IotSimAuthentication extends AbstractAuthenticationToken {

    private final Jwt jwt;
    private final IotSimPrincipal principal;

    public IotSimAuthentication(Jwt jwt, IotSimPrincipal principal,
            Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.jwt = jwt;
        this.principal = principal;
        setAuthenticated(true);
    }

    /** The validated raw {@link Jwt} token. */
    @Override
    public Jwt getCredentials() {
        return jwt;
    }

    /** The converted {@link IotSimPrincipal}. */
    @Override
    public IotSimPrincipal getPrincipal() {
        return principal;
    }

    /** Subject from the principal (used by Spring Security for logging / auditing). */
    @Override
    public String getName() {
        return principal.subject();
    }
}
