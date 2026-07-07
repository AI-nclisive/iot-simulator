package com.ainclusive.iotsim.api.lease;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ainclusive.iotsim.api.lease.EditLeaseController.EditLeaseResponse;
import com.ainclusive.iotsim.api.security.IotSimPrincipal;
import com.ainclusive.iotsim.domain.auth.EditLease;
import com.ainclusive.iotsim.domain.auth.EditLeaseService;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

/**
 * POJO unit tests for {@link EditLeaseController} (IS-081).
 *
 * <p>Tests delegation logic, type-mapping, holder extraction, and response shape
 * without a Spring context (repo convention — see ScenarioControllerTest).
 * HTTP-layer and authorization concerns are covered by {@link EditLeaseControllerMvcTest}.
 */
class EditLeaseControllerTest {

    private static final String PROJECT = "proj-1";
    private static final String OBJECT_ID = "ds-42";
    private static final String HOLDER = "alice";

    private EditLeaseService service;
    private EditLeaseController controller;

    @BeforeEach
    void setUp() {
        service = mock(EditLeaseService.class);
        controller = new EditLeaseController(service);
    }

    // ── acquire ──────────────────────────────────────────────────────────────────

    @Test
    void acquire_dataSources_returns200WithLease() {
        EditLease lease = lease(HOLDER);
        given(service.acquire(EditLeaseService.TYPE_DATA_SOURCE, OBJECT_ID, HOLDER)).willReturn(lease);

        EditLeaseResponse resp = controller.acquire(PROJECT, "data-sources", OBJECT_ID, auth(HOLDER));

        assertThat(resp.objectType()).isEqualTo("data-source");
        assertThat(resp.objectId()).isEqualTo(OBJECT_ID);
        assertThat(resp.holder()).isEqualTo(HOLDER);
        assertThat(resp.heldByCurrentUser()).isTrue();
    }

    @Test
    void acquire_scenarios_mapsToScenarioType() {
        EditLease lease = lease(HOLDER);
        given(service.acquire(EditLeaseService.TYPE_SCENARIO, OBJECT_ID, HOLDER)).willReturn(lease);

        controller.acquire(PROJECT, "scenarios", OBJECT_ID, auth(HOLDER));

        verify(service).acquire(EditLeaseService.TYPE_SCENARIO, OBJECT_ID, HOLDER);
    }

    @Test
    void acquire_conflictingLease_heldByCurrentUserIsFalse() {
        EditLease otherLease = lease("bob");
        given(service.acquire(EditLeaseService.TYPE_DATA_SOURCE, OBJECT_ID, HOLDER))
                .willReturn(otherLease);

        EditLeaseResponse resp = controller.acquire(PROJECT, "data-sources", OBJECT_ID, auth(HOLDER));

        assertThat(resp.holder()).isEqualTo("bob");
        assertThat(resp.heldByCurrentUser()).isFalse();
    }

    // ── release ───────────────────────────────────────────────────────────────────

    @Test
    void release_whenReleased_returns204() {
        given(service.release(EditLeaseService.TYPE_DATA_SOURCE, OBJECT_ID, HOLDER)).willReturn(true);

        ResponseEntity<Void> resp = controller.release(PROJECT, "data-sources", OBJECT_ID, auth(HOLDER));

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void release_whenNotHeld_throwsResourceNotFound() {
        given(service.release(EditLeaseService.TYPE_DATA_SOURCE, OBJECT_ID, HOLDER)).willReturn(false);

        assertThatThrownBy(() -> controller.release(PROJECT, "data-sources", OBJECT_ID, auth(HOLDER)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── getActive ─────────────────────────────────────────────────────────────────

    @Test
    void getActive_existingLease_returns200() {
        EditLease lease = lease(HOLDER);
        given(service.findActive(EditLeaseService.TYPE_DATA_SOURCE, OBJECT_ID))
                .willReturn(Optional.of(lease));

        EditLeaseResponse resp = controller.getActive(PROJECT, "data-sources", OBJECT_ID, auth(HOLDER));

        assertThat(resp.holder()).isEqualTo(HOLDER);
        assertThat(resp.heldByCurrentUser()).isTrue();
    }

    @Test
    void getActive_noActiveLease_throwsResourceNotFound() {
        given(service.findActive(EditLeaseService.TYPE_DATA_SOURCE, OBJECT_ID))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> controller.getActive(PROJECT, "data-sources", OBJECT_ID, auth(HOLDER)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── type mapping ──────────────────────────────────────────────────────────────

    @Test
    void resolveType_dataSources_mapsToDataSource() {
        assertThat(EditLeaseController.resolveType("data-sources"))
                .isEqualTo(EditLeaseService.TYPE_DATA_SOURCE);
    }

    @Test
    void resolveType_scenarios_mapsToScenario() {
        assertThat(EditLeaseController.resolveType("scenarios"))
                .isEqualTo(EditLeaseService.TYPE_SCENARIO);
    }

    @Test
    void resolveType_unknownValue_throwsIllegalArgument() {
        assertThatThrownBy(() -> EditLeaseController.resolveType("widgets"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("widgets");
    }

    // ── holder extraction ─────────────────────────────────────────────────────────

    @Test
    void holderFrom_iotSimPrincipal_returnsSubject() {
        assertThat(EditLeaseController.holderFrom(auth(HOLDER))).isEqualTo(HOLDER);
    }

    @Test
    void holderFrom_nullAuth_returnsLocal() {
        assertThat(EditLeaseController.holderFrom(null)).isEqualTo("local");
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private static EditLease lease(String holder) {
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        return new EditLease("data-source", OBJECT_ID, holder, now, now.plusMinutes(5));
    }

    /** Builds an authentication whose principal is an {@link IotSimPrincipal}. */
    private static org.springframework.security.core.Authentication auth(String subject) {
        IotSimPrincipal principal = new IotSimPrincipal(subject, Map.of("sub", subject), List.of());
        return new UsernamePasswordAuthenticationToken(principal, null, List.of());
    }
}
