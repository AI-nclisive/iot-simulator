package com.ainclusive.iotsim.domain.runtimeevent;

import java.time.Instant;

/**
 * One runtime event in the history view (IS-055). {@code at} is the event time;
 * {@code dataSourceId} and {@code runId} are nullable (a project-level event need
 * not name a source or run). {@code payloadJson} is the raw event payload as a
 * JSON document; the API layer renders it as a nested object.
 */
public record RuntimeEventView(
        long id,
        String type,
        Instant at,
        String dataSourceId,
        String runId,
        String payloadJson) {
}
