package com.ainclusive.iotsim.app.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Object-storage configuration.
 *
 * <ul>
 *   <li>{@code filesystem.baseDir} is the root directory for the filesystem
 *       {@link com.ainclusive.iotsim.platform.storage.ObjectStore} adapter — the
 *       local default (decision D3). It holds large artifacts only (evidence
 *       exports, prepared-data imports); no blobs live in Postgres.
 * </ul>
 *
 * <p>The S3-compatible adapter for shared mode lands later; it will add its own
 * sub-properties here. See backend-specs/08_AUTH_AND_MODES.md.
 */
@ConfigurationProperties(prefix = "iotsim.storage")
public record StorageProperties(Filesystem filesystem) {

    public StorageProperties {
        filesystem = filesystem == null ? new Filesystem(null) : filesystem;
    }

    /** Filesystem-adapter tuning. */
    public record Filesystem(String baseDir) {

        public Filesystem {
            baseDir = (baseDir == null || baseDir.isBlank()) ? "data/objects" : baseDir;
        }
    }
}
