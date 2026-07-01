package com.ainclusive.iotsim.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * Unit tests for {@link IotSimPrincipal} (IS-075).
 */
class IotSimPrincipalTest {

    @Test
    void localPrincipalIsNotAuthenticated() {
        IotSimPrincipal principal = new IotSimPrincipal(
                LocalPrincipalFilter.LOCAL_PRINCIPAL, Map.of(), List.of());

        assertThat(principal.isAuthenticated()).isFalse();
    }

    @Test
    void jwtPrincipalIsAuthenticated() {
        // JWT tokens always carry at least iss/iat/exp/sub, so claims are non-empty.
        IotSimPrincipal principal = new IotSimPrincipal(
                "user-42",
                Map.of("iss", "https://idp.example/realms/iotsim"),
                List.of());

        assertThat(principal.isAuthenticated()).isTrue();
    }

    @Test
    void jwtPrincipalWithSubEqualToLocalSentinelIsStillAuthenticated() {
        // An IdP that issues sub="local" must not be mis-identified as the local principal.
        IotSimPrincipal principal = new IotSimPrincipal(
                LocalPrincipalFilter.LOCAL_PRINCIPAL,
                Map.of("iss", "https://idp.example/realms/iotsim"),
                List.of());

        assertThat(principal.isAuthenticated()).isTrue();
    }

    @Test
    void claimsAreUnmodifiable() {
        Map<String, Object> original = new java.util.HashMap<>();
        original.put("email", "test@example.com");
        IotSimPrincipal principal = new IotSimPrincipal("user-42", original, List.of());

        assertThatThrownBy(() -> principal.claims().put("extra", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void mutatingOriginalClaimsMapDoesNotAffectPrincipal() {
        java.util.HashMap<String, Object> original = new java.util.HashMap<>();
        original.put("email", "test@example.com");
        IotSimPrincipal principal = new IotSimPrincipal("user-42", original, List.of());

        original.put("extra", "mutated");

        // Principal holds a snapshot taken at construction time
        assertThat(principal.claims()).doesNotContainKey("extra");
        assertThat(principal.claims()).containsEntry("email", "test@example.com");
    }

    @Test
    void claimAccessorReturnsValueOrNull() {
        IotSimPrincipal principal = new IotSimPrincipal(
                "user-42", Map.of("email", "x@example.com"), List.of());

        assertThat(principal.claim("email")).isEqualTo("x@example.com");
        assertThat(principal.claim("missing")).isNull();
    }

    @Test
    void authoritiesArePreserved() {
        List<SimpleGrantedAuthority> auths =
                List.of(new SimpleGrantedAuthority("ROLE_admin"));
        IotSimPrincipal principal = new IotSimPrincipal("user-42", Map.of(), auths);

        assertThat(principal.authorities())
                .extracting(a -> a.getAuthority())
                .containsExactly("ROLE_admin");
    }

    @Test
    void nullSubjectThrows() {
        assertThatThrownBy(() -> new IotSimPrincipal(null, Map.of(), List.of()))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankSubjectThrows() {
        assertThatThrownBy(() -> new IotSimPrincipal("  ", Map.of(), List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("subject must not be blank");
    }

    @Test
    void nullClaimsDefaultsToEmptyMap() {
        IotSimPrincipal principal = new IotSimPrincipal("user-42", null, List.of());

        assertThat(principal.claims()).isEmpty();
    }

    @Test
    void nullAuthoritiesDefaultsToEmptyList() {
        IotSimPrincipal principal = new IotSimPrincipal("user-42", Map.of(), null);

        assertThat(principal.authorities()).isEmpty();
    }
}
