package com.ainclusive.iotsim.api.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Establishes the implicit {@code local} principal in trusted local mode: a single
 * user with full control and no login screen (SPEC "Use Product Without Login").
 * The authenticated OIDC principal used in shared mode is wired by IS-075.
 * See backend-specs/08_AUTH_AND_MODES.md.
 */
public class LocalPrincipalFilter extends OncePerRequestFilter {

    /** Author/owner stamp applied to local-mode writes. */
    public static final String LOCAL_PRINCIPAL = "local";

    // Full control until the flexible permission model (IS-076) replaces these roles.
    private static final List<GrantedAuthority> FULL_CONTROL =
            List.of(new SimpleGrantedAuthority("ROLE_admin"),
                    new SimpleGrantedAuthority("ROLE_user"));

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        SecurityContext context = SecurityContextHolder.getContext();
        if (context.getAuthentication() == null) {
            context.setAuthentication(new UsernamePasswordAuthenticationToken(
                    LOCAL_PRINCIPAL, null, FULL_CONTROL));
        }
        filterChain.doFilter(request, response);
    }
}
