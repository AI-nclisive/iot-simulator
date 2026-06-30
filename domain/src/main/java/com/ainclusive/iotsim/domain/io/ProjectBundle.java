package com.ainclusive.iotsim.domain.io;

import java.io.InputStream;

/**
 * A downloadable project export artifact (IS-073): bytes plus content-type and
 * suggested download filename.
 */
public record ProjectBundle(InputStream content, String contentType, String filename) {

    public static final String CONTENT_TYPE = "application/zip";
    public static final String FILENAME = "project-export.zip";
}
