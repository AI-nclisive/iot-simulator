package com.ainclusive.iotsim.domain.auth;

import com.ainclusive.iotsim.domain.activityevent.ActivityEventService;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.auth.UserRepository;
import com.ainclusive.iotsim.persistence.auth.UserRow;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Admin-facing user management service (IS-118).
 *
 * <p>Wraps {@link UserRepository} to expose list, role-change, and status-change
 * operations used by the admin panel. Authorization is enforced at the controller layer
 * ({@code admin.access} permission) — this service performs no permission checks itself.
 */
@Service
public class AdminUserService {

    private final UserRepository users;
    private final ActivityEventService activity;
    private final ObjectMapper mapper;

    public AdminUserService(UserRepository users, ActivityEventService activity, ObjectMapper mapper) {
        this.users = users;
        this.activity = activity;
        this.mapper = mapper;
    }

    /** Returns all registered users with their current role and status. */
    public List<AdminUserView> listUsers() {
        List<UserRow> all = users.findAll();
        Map<String, List<String>> rolesByUser = users.findAllRoles();
        return all.stream()
                .map(u -> toView(u, rolesByUser.getOrDefault(u.id(), List.of())))
                .toList();
    }

    /**
     * Replaces the user's role set with the single given role.
     *
     * @param userId  the user to update
     * @param newRole bare role name, e.g. {@code "admin"} or {@code "user"}
     * @throws ResourceNotFoundException if the user does not exist
     */
    @Transactional
    public AdminUserView changeRole(String userId, String newRole, String actor) {
        UserRow user = users.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        for (String existing : users.findRoles(userId)) {
            users.removeRole(userId, existing);
        }
        users.assignRole(userId, newRole);
        activity.emit(null, actor, "role_change", "user", userId,
                mapper.createObjectNode().put("role", newRole).toString());
        return toView(user, newRole);
    }

    /**
     * Updates the user's status (e.g. {@code "ACTIVE"}, {@code "SUSPENDED"}).
     *
     * @throws ResourceNotFoundException if the user does not exist
     */
    @Transactional
    public AdminUserView changeStatus(String userId, String newStatus, String actor) {
        UserRow updated = users.updateStatus(userId, newStatus)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        activity.emit(null, actor, "status_change", "user", userId,
                mapper.createObjectNode().put("status", newStatus).toString());
        return toView(updated);
    }

    private AdminUserView toView(UserRow user) {
        return toView(user, users.findRoles(user.id()));
    }

    private AdminUserView toView(UserRow user, List<String> roles) {
        String role = roles.contains(PermissionService.ADMIN_ROLE_NAME)
                ? PermissionService.ADMIN_ROLE_NAME
                : roles.stream().findFirst().orElse("user");
        return new AdminUserView(
                user.id(), user.displayName(), user.subject(),
                user.status(), role, user.lastSeenAt());
    }

    private AdminUserView toView(UserRow user, String role) {
        return new AdminUserView(
                user.id(), user.displayName(), user.subject(),
                user.status(), role, user.lastSeenAt());
    }
}
