package com.ainclusive.iotsim.domain.auth;

import java.time.OffsetDateTime;

/**
 * Domain-level representation of an active edit lease (IS-081).
 *
 * <p>This record is the public face of the edit-lease concept exposed via
 * {@link EditLeaseService}; it avoids leaking the persistence-level
 * {@code EditLeaseRow} type into the API layer.
 *
 * @param objectType service-level type constant (e.g. {@code "data-source"})
 * @param objectId   identifier of the locked object
 * @param holder     subject of the user holding the lease
 * @param acquiredAt when the lease was first acquired (or last renewed)
 * @param expiresAt  when the lease expires (advisory — a scheduler may clean up earlier)
 */
public record EditLease(
        String objectType,
        String objectId,
        String holder,
        OffsetDateTime acquiredAt,
        OffsetDateTime expiresAt) {
}
