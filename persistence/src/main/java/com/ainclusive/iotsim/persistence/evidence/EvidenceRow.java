package com.ainclusive.iotsim.persistence.evidence;

import java.time.OffsetDateTime;

/**
 * Persistence-level projection of an {@code evidence} row: portable proof of what
 * happened in a run (SPEC "Export Run Evidence", P0; backend-specs/03 &amp; 04).
 *
 * <p>{@code runId} is nullable — evidence is captured 0..1 per run and the run link
 * may be cleared if the run is deleted ({@code on delete set null}). {@code status}
 * moves {@code CAPTURING → READY | PARTIAL | EXPORT_FAILED}; {@code objectRef} names
 * the export blob in the ObjectStore once an export completes (null while capturing).
 * {@code manifestJson} is the content manifest (never contains secrets/PKI).
 */
public record EvidenceRow(
        String id,
        String projectId,
        String runId,
        String status,
        String manifestJson,
        String objectRef,
        OffsetDateTime createdAt,
        String createdBy) {
}
