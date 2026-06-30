package com.ainclusive.iotsim.domain.evidence;

/**
 * Export format for an evidence artifact (backend-specs/06, IS-058): the full
 * {@code BUNDLE} (ZIP of manifest + section files, the default) or a compact
 * {@code SUMMARY} (JSON subset for quick sharing — manifest + section counts).
 */
public enum EvidenceFormat {
    BUNDLE,
    SUMMARY
}
