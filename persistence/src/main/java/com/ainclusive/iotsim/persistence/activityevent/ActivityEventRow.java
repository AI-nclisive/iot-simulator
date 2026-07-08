package com.ainclusive.iotsim.persistence.activityevent;

import java.time.OffsetDateTime;

/**
 * Persistence-level projection of an {@code activity_events} row. Append-only:
 * user-initiated actions are recorded and never modified (backend-specs/04).
 *
 * <p>{@code projectId} and {@code objectId} are nullable — admin-level events need
 * not name a project or object.
 */
public record ActivityEventRow(
        long id,
        String projectId,
        String actor,
        String action,
        String objectType,
        String objectId,
        OffsetDateTime at,
        String detailJson) {
}
