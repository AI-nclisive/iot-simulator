package com.ainclusive.iotsim.persistence.auth;

import java.time.OffsetDateTime;

/** Persistence-level projection of a {@code users} row. */
public record UserRow(
        String id,
        String subject,
        String displayName,
        String status,
        OffsetDateTime lastSeenAt) {
}
