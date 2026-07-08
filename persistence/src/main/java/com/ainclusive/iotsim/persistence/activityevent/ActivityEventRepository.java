package com.ainclusive.iotsim.persistence.activityevent;

import java.util.List;

/**
 * Append-only store for user-activity audit events (IS-083, backend-specs/04).
 * Every user action that mutates system state is recorded here. Reads back the
 * activity feed for the team activity view (UI-023/UI-024).
 */
public interface ActivityEventRepository {

    /**
     * Appends one activity event. {@code projectId} and {@code objectId} may be
     * {@code null}; {@code detailJson} may be {@code null} (stored as an empty
     * JSON object). The insert time is used as the event time.
     */
    ActivityEventRow append(String projectId, String actor, String action,
            String objectType, String objectId, String detailJson);

    /**
     * Filtered, keyset-paginated activity query (IS-083). All filters are optional
     * and AND-combined; returns up to {@code limit} rows newest first. Backs both
     * {@code GET .../projects/{id}/activity} and {@code GET .../admin/activity}.
     */
    List<ActivityEventRow> query(ActivityEventQuery filter);
}
