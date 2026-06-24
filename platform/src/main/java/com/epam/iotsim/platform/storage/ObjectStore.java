package com.epam.iotsim.platform.storage;

import java.io.InputStream;
import java.util.Optional;

/**
 * Object-storage port for large artifacts (evidence exports, prepared-data
 * imports, recording/sample blobs). No large blobs live in the relational store.
 *
 * <p>Adapters (decision D3): filesystem (local, default) and S3-compatible
 * (shared). See {@code backend-specs/08_AUTH_AND_MODES.md}.
 */
public interface ObjectStore {

    /** Stores an object and returns an opaque reference for later retrieval. */
    String put(String key, InputStream content, long sizeBytes, String contentType);

    /** Opens an object by reference, if present. */
    Optional<InputStream> get(String ref);

    /** Removes an object. Returns {@code true} if something was deleted. */
    boolean delete(String ref);
}
