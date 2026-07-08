package com.ainclusive.iotsim.domain.activityevent;

import java.time.Instant;

/**
 * Input to {@link ActivityEventService#history}: an activity-event history query
 * (IS-083). All filters are optional and AND-combined; {@code from} is inclusive and
 * {@code to} exclusive. {@code cursor} is the opaque keyset cursor from a previous page;
 * {@code limit} is the requested page size (clamped by the service, {@code null} → default).
 *
 * <p>When {@code projectId} is {@code null} the query spans all projects — this is the
 * admin view. When set it is scoped to a single project.
 */
public record ActivityEventHistoryRequest(
        String projectId,
        String actor,
        String action,
        String objectType,
        Instant from,
        Instant to,
        String cursor,
        Integer limit) {
}
