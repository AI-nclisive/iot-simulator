package com.ainclusive.iotsim.domain.sample;

import java.io.InputStream;

/**
 * A downloadable sample export artifact (IS-070): the ZIP byte stream, its
 * content-type, and the suggested download filename.
 */
public record SampleBundle(InputStream content, String contentType, String filename) {}
