package com.ainclusive.iotsim.persistence.run;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Persistence-level projection of a {@code runs} row plus its {@code run_sources}
 * join: a runtime execution, manual or automated (backend-specs/03 &amp; 04).
 *
 * <p>{@code state} moves {@code QUEUED → RUNNING → STOPPED | FAILED | COMPLETED};
 * {@code startedAt}/{@code endedAt} are null until the run starts/ends.
 * {@code scenarioId} and {@code evidenceId} are nullable links. {@code sourceIds}
 * is the set of data sources the run involves (the {@code run_sources} rows),
 * ordered by id for deterministic reads.
 */
public record RunRow(
        String id,
        String projectId,
        String kind,
        String trigger,
        String initiator,
        String state,
        String scenarioId,
        String evidenceId,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        OffsetDateTime createdAt,
        List<String> sourceIds) {
}
