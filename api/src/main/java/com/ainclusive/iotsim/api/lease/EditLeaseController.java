package com.ainclusive.iotsim.api.lease;

import com.ainclusive.iotsim.api.security.IotSimPrincipal;
import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.auth.EditLease;
import com.ainclusive.iotsim.domain.auth.EditLeaseService;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Advisory edit-lease endpoints (IS-081, backend-specs/05_API_CONTRACT.md §Edit-lease).
 *
 * <p>Supports {@code data-sources} and {@code scenarios} as {@code objectType} path values
 * (plural URL form maps to the service's type constants).
 *
 * <p>Authorization (backend-specs/08_AUTH_AND_MODES.md §Authorization):
 * <ul>
 *   <li>POST acquire / DELETE release — {@link Permission#SOURCE_EDIT} (admin-level; only editors
 *       open editors and therefore need leases).
 *   <li>GET active lease — {@link Permission#OBSERVE} (read-only users need to see who holds a
 *       lease so the UI can display the locked state).
 * </ul>
 */
@RestController
@Tag(name = "Edit Leases")
@RequestMapping("/api/v1/projects/{projectId}/{objectType}/{objectId}/edit-lease")
public class EditLeaseController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";
    private static final String SOURCE_EDIT =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).SOURCE_EDIT)";

    private final EditLeaseService leases;

    public EditLeaseController(EditLeaseService leases) {
        this.leases = leases;
    }

    /**
     * Acquires (or renews) an edit lease for the caller.
     *
     * <p>The lease is granted to the authenticated user. If the object is already leased
     * by someone else, that existing lease is returned; the caller can detect the conflict
     * via the {@code heldByCurrentUser} flag in the response.
     *
     * @param projectId  the project context (validated by upstream authorization)
     * @param objectType URL-form object type: {@code data-sources} or {@code scenarios}
     * @param objectId   identifier of the object to lease
     * @param auth       the current Spring Security authentication (injected by Spring MVC)
     * @return 200 with the current lease (new, renewed, or conflicting)
     */
    @Operation(
            summary = "Acquire or renew an edit lease",
            description = "Grants a time-bounded advisory edit lease to the authenticated user,"
                    + " or renews it if already held. Returns the current lease — check"
                    + " heldByCurrentUser to detect a conflict with another user's lease.")
    @PostMapping
    @PreAuthorize(SOURCE_EDIT)
    public EditLeaseResponse acquire(
            @PathVariable String projectId,
            @PathVariable String objectType,
            @PathVariable String objectId,
            Authentication auth) {
        String type = resolveType(objectType);
        String holder = holderFrom(auth);
        EditLease lease = leases.acquire(type, objectId, holder);
        return EditLeaseResponse.from(lease, holder);
    }

    /**
     * Releases the edit lease held by the caller.
     *
     * @return 204 No Content on success; 404 if no active lease is held by the caller
     */
    @Operation(
            summary = "Release an edit lease",
            description = "Releases the advisory edit lease held by the authenticated user."
                    + " Returns 204 on success, 404 if no active lease is held by this user.")
    @DeleteMapping
    @PreAuthorize(SOURCE_EDIT)
    public ResponseEntity<Void> release(
            @PathVariable String projectId,
            @PathVariable String objectType,
            @PathVariable String objectId,
            Authentication auth) {
        String type = resolveType(objectType);
        String holder = holderFrom(auth);
        boolean released = leases.release(type, objectId, holder);
        if (!released) {
            throw new ResourceNotFoundException("EditLease", objectId);
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Returns the currently active lease, if one exists.
     *
     * @return 200 with the lease; 404 if no active lease
     */
    @Operation(
            summary = "Get the active edit lease",
            description = "Returns the currently active advisory edit lease for the object,"
                    + " or 404 if no lease is active. The heldByCurrentUser flag indicates"
                    + " whether the authenticated user holds the lease.")
    @GetMapping
    @PreAuthorize(OBSERVE)
    public EditLeaseResponse getActive(
            @PathVariable String projectId,
            @PathVariable String objectType,
            @PathVariable String objectId,
            Authentication auth) {
        String type = resolveType(objectType);
        String holder = holderFrom(auth);
        EditLease lease = leases.findActive(type, objectId)
                .orElseThrow(() -> new ResourceNotFoundException("EditLease", objectId));
        return EditLeaseResponse.from(lease, holder);
    }

    // ── internals ────────────────────────────────────────────────────────────────

    /**
     * Maps the URL-form plural objectType to the service's type constant.
     *
     * @throws IllegalArgumentException for unrecognised values (→ 400)
     */
    static String resolveType(String objectType) {
        return switch (objectType) {
            case "data-sources" -> EditLeaseService.TYPE_DATA_SOURCE;
            case "scenarios"    -> EditLeaseService.TYPE_SCENARIO;
            default -> throw new IllegalArgumentException(
                    "unknown objectType: '" + objectType + "'; expected 'data-sources' or 'scenarios'");
        };
    }

    /** Extracts the holder identity from the authentication token. */
    static String holderFrom(Authentication auth) {
        if (auth != null && auth.getPrincipal() instanceof IotSimPrincipal p) {
            return p.subject();
        }
        // Fallback for local mode where the implicit principal is set by LocalPrincipalFilter
        // as a UsernamePasswordAuthenticationToken — getName() returns the subject.
        return auth != null ? auth.getName() : "local";
    }

    /**
     * Response DTO for all edit-lease endpoints.
     *
     * @param objectType       the service-level type constant (e.g. {@code "data-source"})
     * @param objectId         the locked object's identifier
     * @param holder           the identity of the user holding the lease
     * @param expiresAt        ISO-8601 expiry timestamp
     * @param heldByCurrentUser whether the authenticated caller holds the lease
     */
    public record EditLeaseResponse(
            String objectType,
            String objectId,
            String holder,
            String expiresAt,
            boolean heldByCurrentUser) {

        static EditLeaseResponse from(EditLease lease, String caller) {
            return new EditLeaseResponse(
                    lease.objectType(),
                    lease.objectId(),
                    lease.holder(),
                    lease.expiresAt().toInstant().toString(),
                    lease.holder().equals(caller));
        }
    }
}
