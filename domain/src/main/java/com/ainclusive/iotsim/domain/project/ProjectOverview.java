package com.ainclusive.iotsim.domain.project;

/**
 * Per-project rollup for the workspace overview (IS-054). All counts are derived,
 * never persisted; {@code sourcesNeedingAttention} counts data sources whose
 * runtime state is unhealthy (ERROR or STALE). See
 * openspec/specs/api-contract/spec.md ("Dashboard reads").
 */
public record ProjectOverview(
        String projectId,
        String name,
        int configuredSources,
        int runningSources,
        int reusableArtifacts,
        int sourcesNeedingAttention) {
}
