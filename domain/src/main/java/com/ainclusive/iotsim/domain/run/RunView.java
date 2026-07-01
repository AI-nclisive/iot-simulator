package com.ainclusive.iotsim.domain.run;

import java.time.Instant;
import java.util.List;

/** Read projection of a run row, enriched with human-readable labels (IS-089). */
public record RunView(
        String id,
        String projectId,
        String kind,
        String trigger,
        String initiator,
        String state,
        String scenarioId,
        String evidenceId,
        String parentRunId,
        List<String> sourceIds,
        Instant startedAt,
        Instant endedAt,
        Instant createdAt,
        String label,
        String relatedLabel) {
}
