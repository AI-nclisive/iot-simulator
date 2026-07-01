package com.ainclusive.iotsim.persistence.run;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Stores run records and their participating data sources (the {@code run_sources}
 * join). Owns the run lifecycle ({@code QUEUED → RUNNING → terminal}); the live
 * {@code runtimeState} of individual sources is the supervisor's, not this repo's
 * (backend-specs/03 &amp; 04).
 */
public interface RunRepository {

    /**
     * Creates a run in {@code QUEUED} state and records its participating sources.
     * {@code trigger}/{@code initiator} may be {@code null} (fall through to column
     * defaults {@code MANUAL}/{@code local}); {@code scenarioId} and
     * {@code parentRunId} are nullable; {@code sourceIds} may be empty.
     */
    RunRow create(String projectId, String kind, String trigger, String initiator,
            List<String> sourceIds, String scenarioId, String parentRunId);

    /** Backwards-compatible create for standalone (non-child) runs: {@code parentRunId = null}. */
    default RunRow create(String projectId, String kind, String trigger, String initiator,
            List<String> sourceIds, String scenarioId) {
        return create(projectId, kind, trigger, initiator, sourceIds, scenarioId, null);
    }

    /** The run with its {@code sourceIds}, if present. */
    Optional<RunRow> findById(String id);

    /** All runs for a project, newest first (index-backed: project_id, created_at). */
    List<RunRow> findByProject(String projectId);

    /**
     * Active runs for a project: {@code state IN (RUNNING, QUEUED)}, newest first.
     * Used by the dashboard overview panel (IS-122).
     */
    List<RunRow> findActiveByProject(String projectId);

    /** Moves a run to {@code RUNNING} and stamps {@code startedAt}. */
    RunRow start(String id, OffsetDateTime startedAt);

    /**
     * Moves a run to a terminal state ({@code STOPPED}, {@code FAILED}, or
     * {@code COMPLETED}) and stamps {@code endedAt}.
     */
    RunRow end(String id, String terminalState, OffsetDateTime endedAt);

    /** Links the run to its evidence record (the {@code runs.evidence_id} side). */
    RunRow linkEvidence(String runId, String evidenceId);
}
