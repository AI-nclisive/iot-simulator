package com.ainclusive.iotsim.api.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Unit tests for {@link JwtPrincipalConverter} (IS-075).
 *
 * <p>Verifies that JWT claims are correctly mapped to {@link IotSimPrincipal} fields
 * and that the role-claim extraction handles edge cases (missing claim, wrong type,
 * blank roles, custom claim name).
 */
class JwtPrincipalConverterTest {

    private static Jwt buildJwt(String subject, Map<String, Object> extraClaims) {
        Map<String, Object> headers = Map.of("alg", "RS256");
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("sub", subject);
        claims.put("iss", "https://idp.example/realms/iotsim");
        claims.put("iat", Instant.now());
        claims.put("exp", Instant.now().plusSeconds(3600));
        claims.putAll(extraClaims);
        return new Jwt("mock-token-value",
                Instant.now(), Instant.now().plusSeconds(3600),
                headers, claims);
    }

    @Test
    void convertsSubjectToAuthenticationName() {
        JwtPrincipalConverter converter = new JwtPrincipalConverter();
        Jwt jwt = buildJwt("user-42", Map.of());

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getName()).isEqualTo("user-42");
    }

    @Test
    void principalIsIotSimPrincipal() {
        JwtPrincipalConverter converter = new JwtPrincipalConverter();
        Jwt jwt = buildJwt("user-42", Map.of("roles", List.of("admin")));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getPrincipal()).isInstanceOf(IotSimPrincipal.class);
        IotSimPrincipal principal = (IotSimPrincipal) token.getPrincipal();
        assertThat(principal.subject()).isEqualTo("user-42");
        assertThat(principal.claims()).containsKey("roles");
    }

    @Test
    void extractsRolesFromDefaultRolesClaim() {
        JwtPrincipalConverter converter = new JwtPrincipalConverter();
        Jwt jwt = buildJwt("user-42", Map.of("roles", List.of("admin", "user")));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_admin", "ROLE_user");
    }

    @Test
    void extractsRolesFromCustomClaim() {
        JwtPrincipalConverter converter = new JwtPrincipalConverter("groups");
        Jwt jwt = buildJwt("user-99", Map.of("groups", List.of("admin")));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_admin");
    }

    @Test
    void returnsEmptyAuthoritiesWhenRolesClaimAbsent() {
        JwtPrincipalConverter converter = new JwtPrincipalConverter();
        Jwt jwt = buildJwt("user-no-roles", Map.of());

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void returnsEmptyAuthoritiesWhenRolesClaimIsWrongType() {
        JwtPrincipalConverter converter = new JwtPrincipalConverter();
        // claim exists but is a String, not a List
        Jwt jwt = buildJwt("user-bad-type", Map.of("roles", "admin"));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities()).isEmpty();
    }

    @Test
    void filtersBlankRoles() {
        JwtPrincipalConverter converter = new JwtPrincipalConverter();
        Jwt jwt = buildJwt("user-blanks", Map.of("roles", List.of("admin", "  ", "")));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_admin");
    }

    @Test
    void nullOrBlankRolesClaimFallsBackToDefault() {
        // null and blank both fall back to the default "roles" claim
        JwtPrincipalConverter converterNull = new JwtPrincipalConverter(null);
        JwtPrincipalConverter converterBlank = new JwtPrincipalConverter("  ");

        Jwt jwt = buildJwt("user-x", Map.of("roles", List.of("user")));

        assertThat(converterNull.convert(jwt).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_user");
        assertThat(converterBlank.convert(jwt).getAuthorities())
                .extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_user");
    }

    @Test
    void tokenIsAuthenticated() {
        JwtPrincipalConverter converter = new JwtPrincipalConverter();
        Jwt jwt = buildJwt("user-42", Map.of("roles", List.of("user")));

        AbstractAuthenticationToken token = converter.convert(jwt);

        assertThat(token.isAuthenticated()).isTrue();
    }
}
