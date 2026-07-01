package com.ainclusive.iotsim.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.meta.MetaController;
import com.ainclusive.iotsim.api.project.ProjectController;
import com.ainclusive.iotsim.api.security.SecurityConfig;
import com.ainclusive.iotsim.api.security.SharedPermissionService;
import com.ainclusive.iotsim.domain.project.Project;
import com.ainclusive.iotsim.domain.project.ProjectService;
import com.ainclusive.iotsim.domain.support.Page;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * IS-077: verifies that authorization enforcement works in shared mode.
 *
 * <ul>
 *   <li>A {@code user}-role JWT can read (OBSERVE) but cannot mutate (PROJECT_EDIT → 403).
 *   <li>An {@code admin}-role JWT can both read and mutate.
 *   <li>An authenticated JWT with no recognised roles is denied on both read and write.
 *   <li>An unauthenticated request is rejected with 401.
 * </ul>
 */
@WebMvcTest(controllers = {MetaController.class, ProjectController.class})
@Import({SecurityConfig.class, SharedPermissionService.class})
@TestPropertySource(properties = "iotsim.mode=shared")
class AuthorizationEnforcementTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @MockitoBean
    ProjectService projectService;

    private static Project sampleProject() {
        Instant now = Instant.now();
        return new Project("p1", "Test", null,
                Project.ProjectStatus.ACTIVE, now, now, "admin", 0);
    }

    // ── read endpoints (OBSERVE) ──────────────────────────────────────────────

    @Test
    void userRoleCanReadProjects() throws Exception {
        given(projectService.listPaged(any(), any(), any()))
                .willReturn(new Page<>(List.of(), null, 20));
        mockMvc.perform(get("/api/v1/projects")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_user"))))
                .andExpect(status().isOk());
    }

    @Test
    void adminRoleCanReadProjects() throws Exception {
        given(projectService.listPaged(any(), any(), any()))
                .willReturn(new Page<>(List.of(), null, 20));
        mockMvc.perform(get("/api/v1/projects")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isOk());
    }

    @Test
    void noRecognisedRoleIsRejectedOnRead() throws Exception {
        mockMvc.perform(get("/api/v1/projects")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_viewer"))))
                .andExpect(status().isForbidden());
    }

    // ── write endpoints (PROJECT_EDIT — admin only) ───────────────────────────

    @Test
    void userRoleCannotCreateProject() throws Exception {
        mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json")
                        .content("{\"name\":\"Test\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_user"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminRoleCanCreateProject() throws Exception {
        given(projectService.create(any(), any(), any())).willReturn(sampleProject());
        mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json")
                        .content("{\"name\":\"Test\"}")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_admin"))))
                .andExpect(status().isCreated());
    }

    @Test
    void unauthenticatedRequestIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isUnauthorized());
    }
}
