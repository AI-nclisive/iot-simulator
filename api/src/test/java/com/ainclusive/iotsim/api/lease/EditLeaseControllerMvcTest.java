package com.ainclusive.iotsim.api.lease;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainclusive.iotsim.api.error.GlobalExceptionHandler;
import com.ainclusive.iotsim.api.security.PermissionService;
import com.ainclusive.iotsim.domain.auth.EditLease;
import com.ainclusive.iotsim.domain.auth.EditLeaseService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.security.autoconfigure.SecurityAutoConfiguration;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.security.autoconfigure.web.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-layer (MockMvc) tests for {@link EditLeaseController} (IS-081).
 *
 * <p>Tests HTTP-layer concerns: status codes, JSON shape, and GlobalExceptionHandler mappings.
 * Security is excluded so the tests focus on HTTP routing and response structure.
 * The 403 case is verified by testing the exception handler's behaviour when
 * {@code AccessDeniedException} propagates from a service call (this exercises the same handler
 * that {@code @PreAuthorize} triggers in production; authorization enforcement is unit-tested
 * in {@link com.ainclusive.iotsim.api.security.SharedPermissionServiceTest}).
 */
@WebMvcTest(
        value = EditLeaseController.class,
        excludeAutoConfiguration = {
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            UserDetailsServiceAutoConfiguration.class,
        })
@Import(GlobalExceptionHandler.class)
class EditLeaseControllerMvcTest {

    private static final String BASE =
            "/api/v1/projects/p1/data-sources/ds-42/edit-lease";
    private static final String SCENARIO_BASE =
            "/api/v1/projects/p1/scenarios/scn-1/edit-lease";
    private static final String UNKNOWN_BASE =
            "/api/v1/projects/p1/widgets/w-1/edit-lease";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    EditLeaseService leaseService;

    @MockitoBean
    PermissionService permissionService;

    @BeforeEach
    void stubPermissions() {
        given(permissionService.hasPermission(any(), any())).willReturn(true);
    }

    private static EditLease sampleLease(String holder) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new EditLease("data-source", "ds-42", holder, now, now.plusMinutes(5));
    }

    // ── POST acquire ─────────────────────────────────────────────────────────────

    @Test
    void acquireReturns200WithLeaseBody() throws Exception {
        given(leaseService.acquire(eq("data-source"), eq("ds-42"), any()))
                .willReturn(sampleLease("alice"));

        mvc.perform(post(BASE))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.objectType").value("data-source"))
                .andExpect(jsonPath("$.objectId").value("ds-42"))
                .andExpect(jsonPath("$.holder").value("alice"))
                .andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(jsonPath("$.heldByCurrentUser").isBoolean());
    }

    /**
     * Tests the 403 response shape via the GlobalExceptionHandler.
     *
     * <p>In production, {@code @PreAuthorize} throws {@link AccessDeniedException} when
     * {@link PermissionService#hasPermission} returns {@code false}. Here we simulate that
     * by making the service throw it directly — the handler mapping is the same in both cases.
     */
    @Test
    void acquireByNonEditorReturns403() throws Exception {
        given(leaseService.acquire(any(), any(), any()))
                .willThrow(new AccessDeniedException("forbidden"));

        mvc.perform(post(BASE))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(403));
    }

    // ── DELETE release ────────────────────────────────────────────────────────────

    @Test
    void releaseReturns204WhenHeld() throws Exception {
        given(leaseService.release(eq("data-source"), eq("ds-42"), any())).willReturn(true);

        mvc.perform(delete(BASE))
                .andExpect(status().isNoContent());
    }

    @Test
    void releaseReturns404WhenNotHeld() throws Exception {
        given(leaseService.release(eq("data-source"), eq("ds-42"), any())).willReturn(false);

        mvc.perform(delete(BASE))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── GET active lease ──────────────────────────────────────────────────────────

    @Test
    void getActiveReturns200WhenLeaseExists() throws Exception {
        given(leaseService.findActive("data-source", "ds-42"))
                .willReturn(Optional.of(sampleLease("alice")));

        mvc.perform(get(BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holder").value("alice"));
    }

    @Test
    void getActiveReturns404WhenNoLease() throws Exception {
        given(leaseService.findActive("data-source", "ds-42"))
                .willReturn(Optional.empty());

        mvc.perform(get(BASE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // ── unknown objectType → 400 ─────────────────────────────────────────────────

    @Test
    void unknownObjectTypeReturns400() throws Exception {
        mvc.perform(post(UNKNOWN_BASE))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.status").value(400));
    }

    // ── scenarios objectType ──────────────────────────────────────────────────────

    @Test
    void acquireForScenarioObjectTypeWorks() throws Exception {
        given(leaseService.acquire(eq("scenario"), eq("scn-1"), any()))
                .willReturn(new EditLease("scenario", "scn-1", "alice",
                        OffsetDateTime.now(ZoneOffset.UTC),
                        OffsetDateTime.now(ZoneOffset.UTC).plusMinutes(5)));

        mvc.perform(post(SCENARIO_BASE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectType").value("scenario"));
    }
}
