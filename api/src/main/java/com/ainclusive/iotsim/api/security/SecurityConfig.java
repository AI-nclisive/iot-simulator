package com.ainclusive.iotsim.api.security;

import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Enforces the deployment-mode flag (IS-078) and wires OIDC JWT validation (IS-075):
 * one build, two security postures.
 *
 * <ul>
 *   <li><b>local</b> ({@code iotsim.mode=local}, default): authentication is off;
 *       every request runs as the implicit {@code local} principal with full
 *       control ({@link LocalPrincipalFilter}).
 *   <li><b>shared</b> ({@code iotsim.mode=shared}): workspace endpoints require an
 *       authenticated bearer JWT (OAuth2/OIDC resource server). JWTs are validated
 *       via JWKS using the issuer URI from
 *       {@code spring.security.oauth2.resourceserver.jwt.issuer-uri} (or the
 *       explicit {@code iotsim.oidc.jwks-uri} override).  The validated token is
 *       converted to an {@link IotSimPrincipal} via {@link JwtPrincipalConverter},
 *       which maps the configurable {@code iotsim.oidc.roles-claim} to Spring
 *       {@code GrantedAuthority} values. Health probes and API docs stay public.
 * </ul>
 *
 * Exactly one chain is active per startup ({@link ConditionalOnProperty}).
 * See backend-specs/08_AUTH_AND_MODES.md.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties({DeploymentProperties.class, OidcProperties.class})
public class SecurityConfig {

    /** Reachable without authentication in shared mode (health probes, API docs). */
    private static final String[] PUBLIC_ENDPOINTS = {
        "/actuator/health/**",
        "/actuator/info",
        "/openapi.json",
        "/swagger-ui.html",
        "/swagger-ui/**",
    };

    /**
     * Spring Boot auto-configuration property for the OIDC issuer URI.
     * Used as a fallback when {@link OidcProperties#issuerUri()} is not set.
     */
    private static final String SPRING_ISSUER_URI_PROP =
            "spring.security.oauth2.resourceserver.jwt.issuer-uri";

    @Bean
    @ConditionalOnProperty(name = "iotsim.mode", havingValue = "local", matchIfMissing = true)
    SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .addFilterBefore(new LocalPrincipalFilter(), AuthorizationFilter.class);
        return http.build();
    }

    /**
     * Shared-mode chain: validates bearer JWTs via JWKS and converts them to
     * {@link IotSimPrincipal} using {@link JwtPrincipalConverter} (IS-075).
     *
     * <p>The active {@link JwtDecoder} is resolved as follows:
     * <ol>
     *   <li>If {@code iotsim.oidc.jwks-uri} is set, a {@link NimbusJwtDecoder} is
     *       built directly from that URI — this covers deployments where OIDC
     *       discovery is unavailable (e.g. Cognito without discovery enabled).
     *   <li>Otherwise the Spring Boot auto-configured decoder (driven by
     *       {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}) is used.
     *   <li>If neither is configured, startup fails with an actionable message.
     * </ol>
     *
     * <p>When {@link OidcProperties#audience()} is set the decoder is further wrapped
     * with an audience validator that rejects tokens missing the expected {@code aud}
     * value. The issuer URI for this validator is resolved by preferring
     * {@code iotsim.oidc.issuer-uri} and falling back to the Spring Boot property, so
     * operators who rely solely on the Spring Boot property do not lose issuer-claim
     * enforcement.
     */
    @Bean
    @ConditionalOnProperty(name = "iotsim.mode", havingValue = "shared")
    SecurityFilterChain sharedSecurityFilterChain(HttpSecurity http,
            ObjectProvider<JwtDecoder> jwtDecoders,
            OidcProperties oidcProperties,
            Environment env) throws Exception {
        JwtDecoder jwtDecoder = resolveDecoder(oidcProperties, jwtDecoders);
        String effectiveIssuer = effectiveIssuerUri(oidcProperties.issuerUri(), env);
        JwtDecoder validatingDecoder = applyAudienceValidation(
                jwtDecoder, oidcProperties.audience(), effectiveIssuer);
        JwtPrincipalConverter converter = new JwtPrincipalConverter(oidcProperties.rolesClaim());
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(validatingDecoder)
                                .jwtAuthenticationConverter(converter)));
        return http.build();
    }

    /**
     * Resolves the {@link JwtDecoder} to use, preferring an explicit
     * {@link OidcProperties#jwksUri()} over the Spring Boot auto-configured bean.
     */
    private static JwtDecoder resolveDecoder(OidcProperties props,
            ObjectProvider<JwtDecoder> autoConfigured) {
        if (props.jwksUri() != null && !props.jwksUri().isBlank()) {
            return NimbusJwtDecoder.withJwkSetUri(props.jwksUri()).build();
        }
        JwtDecoder decoder = autoConfigured.getIfAvailable();
        if (decoder == null) {
            throw new IllegalStateException(
                    "iotsim.mode=shared requires an OIDC issuer: set "
                    + "spring.security.oauth2.resourceserver.jwt.issuer-uri (or "
                    + "iotsim.oidc.jwks-uri) so bearer JWTs validate via JWKS. "
                    + "See backend-specs/08_AUTH_AND_MODES.md.");
        }
        return decoder;
    }

    /**
     * Returns the effective issuer URI, preferring {@code iotsim.oidc.issuer-uri}
     * and falling back to {@code spring.security.oauth2.resourceserver.jwt.issuer-uri}.
     */
    private static String effectiveIssuerUri(String oidcIssuerUri, Environment env) {
        if (oidcIssuerUri != null && !oidcIssuerUri.isBlank()) {
            return oidcIssuerUri;
        }
        return env.getProperty(SPRING_ISSUER_URI_PROP);
    }

    /**
     * Adds an {@code aud} claim validator to {@code decoder} when {@code audience} is
     * non-null and non-blank.  Accepts a {@link NimbusJwtDecoder} (the Spring Boot
     * auto-configured default); falls back to the unmodified decoder for any other
     * implementation (e.g. test mocks).
     *
     * <p>{@code setJwtValidator} replaces the entire validation chain, so this method
     * preserves issuer validation by using
     * {@link JwtValidators#createDefaultWithIssuer(String)} when {@code issuerUri} is
     * set, or {@link JwtValidators#createDefault()} otherwise.
     */
    private static JwtDecoder applyAudienceValidation(
            JwtDecoder decoder, String audience, String issuerUri) {
        if (audience == null || audience.isBlank()
                || !(decoder instanceof NimbusJwtDecoder nimbus)) {
            return decoder;
        }
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(issuerUri != null && !issuerUri.isBlank()
                ? JwtValidators.createDefaultWithIssuer(issuerUri)
                : JwtValidators.createDefault());
        validators.add(new JwtClaimValidator<List<String>>(
                "aud", aud -> aud != null && aud.contains(audience)));
        nimbus.setJwtValidator(new DelegatingOAuth2TokenValidator<>(validators));
        return nimbus;
    }
}
