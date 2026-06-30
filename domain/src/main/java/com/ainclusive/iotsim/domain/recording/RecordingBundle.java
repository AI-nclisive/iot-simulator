package com.ainclusive.iotsim.domain.recording;

import java.io.InputStream;

/**
 * A downloadable recording export artifact (IS-070): the ZIP byte stream, its
 * content-type, and the suggested download filename.
 */
public record RecordingBundle(InputStream content, String contentType, String filename) {}
