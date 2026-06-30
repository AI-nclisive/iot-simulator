package com.ainclusive.iotsim.domain.evidence;

import java.io.InputStream;

/**
 * A downloadable evidence artifact (IS-058): the stored bytes plus the
 * {@code contentType} and suggested download {@code filename} for the format that
 * was exported (ZIP bundle or JSON summary).
 */
public record EvidenceBundle(InputStream content, String contentType, String filename) {
}
