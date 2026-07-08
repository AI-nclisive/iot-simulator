package com.ainclusive.iotsim.domain.activityevent;

import java.time.Instant;

/**
 * One activity event in the history view (IS-083). {@code projectId} and {@code objectId}
 * are nullable — admin-level events need not name a project or object. {@code detailJson}
 * is the raw detail payload; the API layer renders it as a nested object.
 */
public record ActivityEventView(
        long id,
        String projectId,
        String actor,
        String action,
        String objectType,
        String objectId,
        Instant at,
        String detailJson) {
}
