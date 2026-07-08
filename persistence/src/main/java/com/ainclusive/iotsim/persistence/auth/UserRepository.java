package com.ainclusive.iotsim.persistence.auth;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * CRUD over the {@code users} table.
 *
 * <p>In shared mode a user record is created on first sight of a JWT {@code sub}
 * (upsert-on-login pattern). Local trusted mode does not require these to be populated.
 */
public interface UserRepository {

    /**
     * Creates a user record for the given OIDC subject if one does not already exist,
     * or updates {@code display_name} and {@code last_seen_at} if it does (upsert).
     *
     * @return the up-to-date user row
     */
    UserRow upsert(String subject, String displayName);

    Optional<UserRow> findById(String id);

    Optional<UserRow> findBySubject(String subject);

    List<UserRow> findAll();

    /**
     * Updates the user's status (e.g. {@code ACTIVE}, {@code SUSPENDED}).
     *
     * @return the updated row, or empty when the user was not found
     */
    Optional<UserRow> updateStatus(String id, String status);

    /** Assigns a role to a user (no-op if already assigned). */
    void assignRole(String userId, String roleName);

    /** Removes a role from a user (no-op if not assigned). */
    void removeRole(String userId, String roleName);

    /** Returns the role names assigned to the given user. */
    List<String> findRoles(String userId);

    /** Returns all user-id → role-name mappings in a single query. */
    Map<String, List<String>> findAllRoles();

    boolean deleteById(String id);
}
