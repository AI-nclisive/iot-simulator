package com.ainclusive.iotsim.persistence.scenario;

import java.time.OffsetDateTime;
import java.util.List;

/** Persistence-level projection of a {@code scenarios} row plus its ordered steps. */
public record ScenarioRow(
        String id,
        String projectId,
        String name,
        String status,
        String deterministicSettings,
        List<ScenarioStepRow> steps,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        long version) {}
