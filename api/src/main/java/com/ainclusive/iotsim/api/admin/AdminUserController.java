package com.ainclusive.iotsim.api.admin;

import com.ainclusive.iotsim.domain.auth.AdminUserService;
import com.ainclusive.iotsim.domain.auth.AdminUserView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin user-management endpoints (IS-118, backend-specs/05_API_CONTRACT.md §Admin).
 *
 * <p>All endpoints require {@code admin.access} permission. In local (trusted) mode the
 * implicit principal holds all permissions, so no token is needed.
 */
@RestController
@Tag(name = "Admin — Users")
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {

    private static final String ADMIN_ACCESS =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).ADMIN_ACCESS)";

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize(ADMIN_ACCESS)
    @Operation(summary = "List all users", description = "Returns all registered users with their current role and status. Admin-only.")
    public AdminUserListResponse listUsers(Authentication auth) {
        List<AdminUserResponse> items = service.listUsers().stream()
                .map(AdminUserResponse::from)
                .toList();
        return new AdminUserListResponse(items);
    }

    @PatchMapping("/{id}/roles")
    @PreAuthorize(ADMIN_ACCESS)
    @Operation(summary = "Change a user's role", description = "Replaces the user's role with the given value. Admin-only.")
    public AdminUserResponse changeRole(
            @PathVariable String id,
            @RequestBody ChangeRoleRequest body,
            Authentication auth) {
        return AdminUserResponse.from(service.changeRole(id, body.role()));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize(ADMIN_ACCESS)
    @Operation(summary = "Change a user's status", description = "Updates the user's status (e.g. ACTIVE, SUSPENDED). Admin-only.")
    public AdminUserResponse changeStatus(
            @PathVariable String id,
            @RequestBody ChangeStatusRequest body,
            Authentication auth) {
        return AdminUserResponse.from(service.changeStatus(id, body.status()));
    }

    // ── DTOs ─────────────────────────────────────────────────────────────────

    public record AdminUserListResponse(List<AdminUserResponse> items) {}

    public record AdminUserResponse(
            String id,
            String displayName,
            String subject,
            String status,
            String role,
            String lastSeenAt) {

        static AdminUserResponse from(AdminUserView v) {
            return new AdminUserResponse(
                    v.id(),
                    v.displayName(),
                    v.subject(),
                    v.status(),
                    v.role(),
                    v.lastSeenAt() != null ? v.lastSeenAt().toInstant().toString() : null);
        }
    }

    public record ChangeRoleRequest(String role) {}

    public record ChangeStatusRequest(String status) {}
}
