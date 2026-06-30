package com.ainclusive.iotsim.domain.evidence;

/**
 * Scenario context in an evidence artifact (backend-specs/06). Populated only for
 * scenario runs; {@code null} on the {@link EvidenceContent} for non-scenario runs
 * (e.g. replay). The full scenario model lands with IS-085/IS-086.
 */
public record ScenarioMetadata(
        String id,
        String name) {
}
