package com.ainclusive.iotsim.domain.evidence;

import java.io.OutputStream;

/**
 * Serializes assembled {@link EvidenceContent} into a portable artifact in one
 * {@link EvidenceFormat} (backend-specs/06). IS-057 shipped the ZIP {@code BUNDLE}
 * ({@link ZipEvidenceArtifactWriter}); IS-058 adds the JSON {@code SUMMARY}
 * ({@link JsonSummaryEvidenceWriter}). Every artifact stamps {@link #FORMAT_VERSION}
 * into its manifest, and implementations must never serialize secrets/PKI.
 */
public interface EvidenceArtifactWriter {

    /** Semver of the artifact format; carried in the manifest (rejection-on-read is IS-091). */
    String FORMAT_VERSION = "1.0.0";

    /** Which export format this writer produces. */
    EvidenceFormat format();

    /** Writes the artifact for {@code content} to {@code out} (the stream stays open). */
    void write(EvidenceContent content, OutputStream out);

    /** MIME type of the produced artifact. */
    String contentType();

    /** Object-store filename for this format, e.g. {@code bundle.zip} / {@code summary.json}. */
    String artifactFilename();

    /** The {@code formatVersion} stamped into the manifest. */
    default String formatVersion() {
        return FORMAT_VERSION;
    }
}
