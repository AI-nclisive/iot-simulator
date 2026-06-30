package com.ainclusive.iotsim.persistence.runtimeevent;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Append-only store for runtime events (the {@code RuntimeEvents} worker stream,
 * IS-048): source start/stop, errors, fault state changes. Reads are time-ordered
 * (newest first) and back the runtime-event history (IS-055). Distinct from the
 * user-activity audit stream, which is never merged in (backend-specs/04).
 */
public interface RuntimeEventRepository {

    /**
     * Appends one runtime event. {@code dataSourceId} and {@code runId} may be
     * {@code null}; {@code at} is the event time and may be {@code null} to default
     * to the insert time; {@code payloadJson} may be {@code null} (stored as an
     * empty JSON object).
     */
    RuntimeEventRow append(String projectId, String dataSourceId, String runId,
            String type, OffsetDateTime at, String payloadJson);

    Optional<RuntimeEventRow> findById(long id);

    /** Events for a project, newest first (index-backed: project_id, at). */
    List<RuntimeEventRow> findByProject(String projectId);

    /** Events for a run, newest first (index-backed: run_id, at). */
    List<RuntimeEventRow> findByRun(String runId);

    /** Events for a single data source, newest first (index-backed: data_source_id, at). */
    List<RuntimeEventRow> findByDataSource(String dataSourceId);

    /**
     * Filtered, keyset-paginated history query (IS-055). Scoped to a project, with
     * optional source/run/type and {@code [from, to)} time filters; returns up to
     * {@code limit} rows newest first. Backs {@code GET .../runtime-events}.
     */
    List<RuntimeEventRow> query(RuntimeEventQuery filter);
}
