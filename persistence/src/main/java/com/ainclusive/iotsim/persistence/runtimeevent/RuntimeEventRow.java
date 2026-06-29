package com.ainclusive.iotsim.persistence.runtimeevent;

import java.time.OffsetDateTime;

/**
 * Persistence-level projection of a {@code runtime_events} row. Append-only:
 * runtime events (source start/stop, errors, fault state changes) are recorded as
 * they happen and never updated. Kept strictly separate from the user-activity
 * audit stream (backend-specs/04).
 *
 * <p>{@code dataSourceId} and {@code runId} are nullable — a project-level event
 * need not name a source or run.
 */
public record RuntimeEventRow(
        long id,
        String projectId,
        String dataSourceId,
        String runId,
        String type,
        OffsetDateTime at,
        String payloadJson) {
}
