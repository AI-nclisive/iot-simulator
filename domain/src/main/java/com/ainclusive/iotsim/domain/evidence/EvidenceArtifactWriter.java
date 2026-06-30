package com.ainclusive.iotsim.domain.evidence;

import java.io.OutputStream;

/**
 * Serializes assembled {@link EvidenceContent} into a portable artifact (IS-057).
 * The default is a ZIP bundle ({@link ZipEvidenceArtifactWriter}); IS-058 extends
 * this seam with the JSON-summary subset and {@code formatVersion} compatibility
 * rules. Implementations must never serialize secrets/PKI (backend-specs/06).
 */
public interface EvidenceArtifactWriter {

    /** Writes the artifact for {@code content} to {@code out} (the stream stays open). */
    void write(EvidenceContent content, OutputStream out);

    /** The {@code formatVersion} this writer stamps into the manifest. */
    String formatVersion();

    /** MIME type of the produced artifact. */
    String contentType();
}
