package com.ainclusive.iotsim.domain.auth;

import java.time.OffsetDateTime;

/** Domain projection of a user for admin management (IS-118). */
public record AdminUserView(
        String id,
        String displayName,
        String subject,
        String status,
        String role,
        OffsetDateTime lastSeenAt) {
}
