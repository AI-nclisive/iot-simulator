package com.ainclusive.iotsim.domain.activityevent;

import com.ainclusive.iotsim.persistence.activityevent.ActivityEventQuery;
import com.ainclusive.iotsim.persistence.activityevent.ActivityEventRepository;
import com.ainclusive.iotsim.persistence.activityevent.ActivityEventRow;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Discards every emit and returns an empty query result — for use in unit tests of other services. */
public final class NoOpActivityEventRepository implements ActivityEventRepository {

    @Override
    public ActivityEventRow append(String projectId, String actor, String action,
            String objectType, String objectId, String detailJson) {
        return new ActivityEventRow(0, projectId, actor, action, objectType, objectId,
                OffsetDateTime.now(ZoneOffset.UTC), "{}");
    }

    @Override
    public List<ActivityEventRow> query(ActivityEventQuery filter) {
        return List.of();
    }
}
