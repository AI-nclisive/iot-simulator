package com.ainclusive.iotsim.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * OIDC resource-server configuration for shared mode (IS-075).
 *
 * <p>The JWKS URI and issuer URI are intentionally kept separate from
 * {@code spring.security.oauth2.resourceserver.jwt.*} so that operator
 * overrides (e.g. offline/internal JWKS cache) are possible without
 * touching Spring Boot's auto-configuration directly.
 *
 * <p>In practice the issuer-uri drives OIDC discovery (JWKS endpoint + issuer
 * validation); set {@code jwks-uri} only when discovery is unavailable (e.g.
 * Cognito without OIDC discovery enabled). Both are optional — absent values
 * fall through to the Spring Boot auto-configuration defaults.
 *
 * <p>See backend-specs/08_AUTH_AND_MODES.md.
 *
 * @param issuerUri  OIDC issuer URI (e.g. {@code https://idp.example/realms/iotsim}).
 *                   Drives issuer validation + JWKS discovery.
 * @param jwksUri    JWKS endpoint URI. Override when auto-discovery is unavailable.
 * @param audience   Expected JWT {@code aud} claim value. Optional; when set, tokens
 *                   without this audience are rejected.
 * @param rolesClaim JWT claim whose value is a {@code List<String>} of product-role
 *                   names (default {@code "roles"}, configurable for IdP variants
 *                   that use {@code "groups"}).
 */
@ConfigurationProperties(prefix = "iotsim.oidc")
public record OidcProperties(
        String issuerUri,
        String jwksUri,
        String audience,
        String rolesClaim) {

    public OidcProperties {
        // Null is valid for all fields (shared mode falls back to Spring Boot defaults).
        if (rolesClaim == null || rolesClaim.isBlank()) {
            rolesClaim = JwtPrincipalConverter.DEFAULT_ROLES_CLAIM;
        }
    }
}
