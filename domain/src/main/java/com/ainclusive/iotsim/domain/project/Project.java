package com.ainclusive.iotsim.domain.project;

import java.time.Instant;

/**
 * A project: the workspace boundary grouping a simulator setup and its reusable
 * artifacts. See {@code backend-specs/03_DOMAIN_MODEL.md}.
 */
public record Project(
        String id,
        String name,
        String description,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        long version) {

    public enum ProjectStatus {
        ACTIVE,
        ARCHIVED
    }
}
