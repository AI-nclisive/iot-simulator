package com.ainclusive.iotsim.app;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.meta.MetaController;
import com.ainclusive.iotsim.api.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * IS-078: in shared mode workspace endpoints require an authenticated bearer JWT.
 * See backend-specs/08_AUTH_AND_MODES.md.
 */
@WebMvcTest(controllers = MetaController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "iotsim.mode=shared")
class SharedModeSecurityTest {

    @Autowired
    MockMvc mockMvc;

    // The resource-server chain needs a JwtDecoder bean to build; jwt() injects the
    // authentication directly, so the decoder is never invoked (real OIDC: IS-075).
    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void rejectsUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/meta"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void allowsAuthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/meta").with(jwt()))
                .andExpect(status().isOk());
    }

    @Test
    void publicEndpointsAreReachableWithoutCredentials() throws Exception {
        // permitAll lets the unauthenticated request past the security chain; the
        // actuator endpoint itself isn't registered in this controller slice, so it
        // resolves to 404 (not the 401 an authenticated-only path returns). Asserting
        // "not 401" guards the PUBLIC_ENDPOINTS permitAll stanza against regressions.
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isNotFound());
    }
}
