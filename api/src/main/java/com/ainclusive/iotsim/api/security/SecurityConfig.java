package com.ainclusive.iotsim.api.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

/**
 * Enforces the deployment-mode flag (IS-078): one build, two security postures.
 *
 * <ul>
 *   <li><b>local</b> ({@code iotsim.mode=local}, default): authentication is off;
 *       every request runs as the implicit {@code local} principal with full
 *       control ({@link LocalPrincipalFilter}).
 *   <li><b>shared</b> ({@code iotsim.mode=shared}): workspace endpoints require an
 *       authenticated bearer JWT (OAuth2/OIDC resource server). Health probes and
 *       API docs stay public. JWKS-based validation is configured via
 *       {@code spring.security.oauth2.resourceserver.jwt.*}; full provider support
 *       (issuer/audience/role mapping) lands in IS-075/IS-076/IS-077.
 * </ul>
 *
 * Exactly one chain is active per startup ({@link ConditionalOnProperty}).
 * See backend-specs/08_AUTH_AND_MODES.md.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(DeploymentProperties.class)
public class SecurityConfig {

    /** Reachable without authentication in shared mode (health probes, API docs). */
    private static final String[] PUBLIC_ENDPOINTS = {
        "/actuator/health/**",
        "/actuator/info",
        "/openapi.json",
        "/swagger-ui.html",
        "/swagger-ui/**",
    };

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

    @Bean
    @ConditionalOnProperty(name = "iotsim.mode", havingValue = "shared")
    SecurityFilterChain sharedSecurityFilterChain(HttpSecurity http,
            ObjectProvider<JwtDecoder> jwtDecoders) throws Exception {
        JwtDecoder jwtDecoder = jwtDecoders.getIfAvailable();
        if (jwtDecoder == null) {
            throw new IllegalStateException(
                    "iotsim.mode=shared requires an OIDC issuer: set "
                    + "spring.security.oauth2.resourceserver.jwt.issuer-uri so bearer JWTs "
                    + "validate via JWKS (full provider support lands in IS-075).");
        }
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(jwtDecoder)));
        return http.build();
    }
}
