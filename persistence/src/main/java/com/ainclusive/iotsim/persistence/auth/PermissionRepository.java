package com.ainclusive.iotsim.persistence.auth;

import java.util.List;
import java.util.Optional;

/**
 * CRUD over the {@code permissions} table.
 *
 * <p>Permissions represent fine-grained capabilities (e.g. {@code source.start},
 * {@code admin.access}). They are seeded by migrations but can be extended at runtime
 * as the model evolves (flexible permission model, D2).
 */
public interface PermissionRepository {

    PermissionRow insert(String name);

    Optional<PermissionRow> findByName(String name);

    List<PermissionRow> findAll();

    boolean deleteByName(String name);
}
