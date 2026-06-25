package com.ainclusive.iotsim.app;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.meta.MetaController;
import com.ainclusive.iotsim.api.security.SecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * IS-078: in local mode the API is open — unauthenticated requests run as the
 * implicit {@code local} principal (no login). See backend-specs/08_AUTH_AND_MODES.md.
 */
@WebMvcTest(controllers = MetaController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "iotsim.mode=local")
class LocalModeSecurityTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    void permitsUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/meta"))
                .andExpect(status().isOk());
    }

    @Test
    void csrfDisabledForWrites() throws Exception {
        // CSRF off: an unauthenticated POST reaches dispatch and yields 405 (no POST
        // handler on /meta), not the 403 a CSRF guard would return first.
        mockMvc.perform(post("/api/v1/meta"))
                .andExpect(status().isMethodNotAllowed());
    }
}
