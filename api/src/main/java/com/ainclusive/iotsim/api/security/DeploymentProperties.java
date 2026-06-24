package com.ainclusive.iotsim.api.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the deployment-mode flag {@code iotsim.mode} (default
 * {@link DeploymentMode#LOCAL}). An unrecognized value fails fast at startup
 * (enum bind error), so the mode is always well-defined.
 * See backend-specs/08_AUTH_AND_MODES.md.
 */
@ConfigurationProperties(prefix = "iotsim")
public record DeploymentProperties(DeploymentMode mode) {

    public DeploymentProperties {
        mode = mode == null ? DeploymentMode.LOCAL : mode;
    }

    public boolean isShared() {
        return mode == DeploymentMode.SHARED;
    }
}
