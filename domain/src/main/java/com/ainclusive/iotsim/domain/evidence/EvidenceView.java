package com.ainclusive.iotsim.domain.evidence;

import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import java.time.Instant;

/**
 * Domain view of an evidence record (IS-057): the metadata the API exposes, mapped
 * off the persistence row so the api layer never depends on persistence. {@code
 * manifestJson} is the content manifest (JSON object, no secrets); {@code objectRef}
 * is the stored bundle reference once exported (else {@code null}).
 */
public record EvidenceView(
        String id,
        String projectId,
        String runId,
        String status,
        String manifestJson,
        String objectRef,
        Instant createdAt,
        String createdBy) {

    static EvidenceView from(EvidenceRow row) {
        return new EvidenceView(row.id(), row.projectId(), row.runId(), row.status(),
                row.manifestJson(), row.objectRef(),
                row.createdAt() == null ? null : row.createdAt().toInstant(), row.createdBy());
    }
}
