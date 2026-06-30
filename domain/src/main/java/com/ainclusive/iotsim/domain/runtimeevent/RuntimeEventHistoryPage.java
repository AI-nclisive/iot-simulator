package com.ainclusive.iotsim.domain.runtimeevent;

import java.util.List;

/**
 * One page of runtime-event history (IS-055): the events (newest first) plus an
 * opaque {@code nextCursor} for the following page, or {@code null} when this is
 * the last page.
 */
public record RuntimeEventHistoryPage(List<RuntimeEventView> events, String nextCursor) {
}
