package com.ainclusive.iotsim.domain.activityevent;

import java.util.List;

/**
 * One page of activity-event history (IS-083): the events (newest first) plus an
 * opaque {@code nextCursor} for the following page, or {@code null} when this is
 * the last page.
 */
public record ActivityEventHistoryPage(List<ActivityEventView> events, String nextCursor) {
}
