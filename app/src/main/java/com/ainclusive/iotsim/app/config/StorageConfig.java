package com.ainclusive.iotsim.app.config;

import com.ainclusive.iotsim.platform.storage.FilesystemObjectStore;
import com.ainclusive.iotsim.platform.storage.ObjectStore;
import java.nio.file.Path;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link ObjectStore}. The default is the local filesystem adapter
 * rooted at {@code iotsim.storage.filesystem.base-dir}; the S3-compatible adapter
 * for shared mode can be selected here later without touching the domain
 * (backend-specs/08_AUTH_AND_MODES.md).
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public ObjectStore objectStore(StorageProperties props) {
        return new FilesystemObjectStore(Path.of(props.filesystem().baseDir()));
    }
}
