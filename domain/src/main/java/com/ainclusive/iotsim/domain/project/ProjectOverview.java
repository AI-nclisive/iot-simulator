package com.ainclusive.iotsim.domain.project;

/**
 * Per-project rollup for the workspace overview (IS-054). All counts are derived,
 * never persisted; {@code sourcesNeedingAttention} counts data sources whose
 * runtime state is unhealthy (ERROR or STALE). See
 * docs/superpowers/specs/2026-06-30-is-054-project-overview-design.md.
 */
public record ProjectOverview(
        String projectId,
        String name,
        int configuredSources,
        int runningSources,
        int reusableArtifacts,
        int sourcesNeedingAttention) {
}
