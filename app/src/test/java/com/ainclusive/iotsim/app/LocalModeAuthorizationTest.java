package com.ainclusive.iotsim.app;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.meta.MetaController;
import com.ainclusive.iotsim.api.project.ProjectController;
import com.ainclusive.iotsim.api.security.LocalPermissionService;
import com.ainclusive.iotsim.api.security.SecurityConfig;
import com.ainclusive.iotsim.domain.project.ProjectService;
import com.ainclusive.iotsim.domain.support.Page;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * IS-077: in local mode authorization is a no-op — all requests are permitted
 * regardless of the endpoint's {@code @PreAuthorize} annotation.
 *
 * <p>The {@link LocalPermissionService} always returns {@code true}, so both read
 * (OBSERVE) and write (PROJECT_EDIT) endpoints are reachable without credentials.
 * This preserves the "Use Product Without Login" guarantee (SPEC, IS-078).
 */
@WebMvcTest(controllers = {MetaController.class, ProjectController.class})
@Import({SecurityConfig.class, LocalPermissionService.class})
@TestPropertySource(properties = "iotsim.mode=local")
class LocalModeAuthorizationTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ProjectService projectService;

    @Test
    void unauthenticatedGetIsPermittedInLocalMode() throws Exception {
        given(projectService.listPaged(any(), any(), any()))
                .willReturn(new Page<>(List.of(), null, 20));
        mockMvc.perform(get("/api/v1/projects"))
                .andExpect(status().isOk());
    }

    @Test
    void unauthenticatedWriteIsNotBlockedByAuthorizationInLocalMode() throws Exception {
        // In local mode the @PreAuthorize annotation resolves to true (LocalPermissionService),
        // so the request reaches the controller. The controller throws IllegalArgumentException
        // on null/blank body → 400, but not 403 (authorization failure).
        mockMvc.perform(post("/api/v1/projects")
                        .contentType("application/json")
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());   // 400 (validation), not 403 (authorization)
    }
}
