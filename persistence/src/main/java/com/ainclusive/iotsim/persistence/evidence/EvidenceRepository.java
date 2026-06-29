package com.ainclusive.iotsim.persistence.evidence;

import java.util.List;
import java.util.Optional;

/**
 * Stores evidence metadata: status, content manifest, and the ObjectStore blob
 * reference once exported (backend-specs/04). The captured artifacts themselves
 * (value timelines, runtime events, the export bundle) live elsewhere; this repo
 * owns the evidence record and its lifecycle.
 */
public interface EvidenceRepository {

    /**
     * Opens an evidence record for a run in {@code CAPTURING} status with an empty
     * manifest. {@code runId} may be {@code null} (the run link can be wired later or
     * cleared on run delete); {@code createdBy} may be {@code null} to fall through to
     * the column default ({@code local}).
     */
    EvidenceRow create(String projectId, String runId, String createdBy);

    Optional<EvidenceRow> findById(String id);

    /** The evidence for a run, if any (0..1 per run). */
    Optional<EvidenceRow> findByRun(String runId);

    /** All evidence for a project, newest first. */
    List<EvidenceRow> findByProject(String projectId);

    /** Replaces the content manifest (JSON object). */
    EvidenceRow updateManifest(String id, String manifestJson);

    /**
     * Records the terminal status of an export and the resulting ObjectStore blob
     * reference. {@code objectRef} may be {@code null} when no blob was produced
     * (e.g. {@code EXPORT_FAILED}).
     */
    EvidenceRow updateStatus(String id, String status, String objectRef);
}
