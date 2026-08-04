package com.ainclusive.iotsim.domain.evidence;

import java.time.Instant;
import java.util.List;

/**
 * Manifest of an evidence artifact (openspec/specs/artifact-formats/spec.md): always-visible origin
 * (which run, who initiated, how complete) plus source/scenario context. Carries
 * no secrets. {@code scenarioId} and {@code recordingId} are nullable (a replay run
 * has a recording but no scenario).
 */
public record EvidenceManifest(
        String formatVersion,
        String runId,
        String kind,
        String trigger,
        String initiator,
        Instant startedAt,
        Instant endedAt,
        Completeness completeness,
        List<String> sourceIds,
        String scenarioId,
        String recordingId) {
}
