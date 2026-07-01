package com.ainclusive.iotsim.persistence.auth;

import java.time.OffsetDateTime;

/** Persistence-level projection of an {@code edit_leases} row. */
public record EditLeaseRow(
        String objectType,
        String objectId,
        String holder,
        OffsetDateTime acquiredAt,
        OffsetDateTime expiresAt) {
}
