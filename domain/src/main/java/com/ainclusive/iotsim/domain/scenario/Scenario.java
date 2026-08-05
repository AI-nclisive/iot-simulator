package com.ainclusive.iotsim.domain.scenario;

import java.time.Instant;
import java.util.List;

/** A test flow: an ordered list of typed steps (openspec/specs/domain-model/spec.md). */
public record Scenario(
        String id,
        String projectId,
        String name,
        String status,
        String deterministicSettings,
        List<ScenarioStep> steps,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        long version) {}
