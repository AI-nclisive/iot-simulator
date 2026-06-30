package com.ainclusive.iotsim.domain.io;

import java.time.Instant;
import java.util.List;

/**
 * Top-level manifest of a project export ZIP (backend-specs/06_ARTIFACT_FORMATS.md).
 *
 * <p>Carries {@code formatVersion} (semver), project metadata, content index,
 * and per-entry checksums. Secrets, credentials, and PKI material are never
 * included (enforced at the export builder).
 */
public record ProjectManifest(
        String formatVersion,
        Instant exportedAt,
        String productVersion,
        ProjectInfo project,
        List<ManifestEntry> entries) {

    /** Semver of the project export format. Reject on import if major differs or version is newer. */
    public static final String FORMAT_VERSION = "1.0.0";

    /** Immutable snapshot of project metadata (no secrets). */
    public record ProjectInfo(
            String id,
            String name,
            String description,
            String status,
            Instant createdAt,
            String createdBy) {}

    /** Describes a single file entry inside the ZIP: path + SHA-256 checksum. */
    public record ManifestEntry(String path, String sha256) {}
}
