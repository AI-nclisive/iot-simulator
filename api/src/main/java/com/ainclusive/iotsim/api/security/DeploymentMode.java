package com.ainclusive.iotsim.api.security;

/**
 * Deployment mode — one build serves both. Selected by configuration
 * ({@code iotsim.mode} / {@code IOTSIM_MODE}), never by a separate build.
 * See backend-specs/08_AUTH_AND_MODES.md.
 */
public enum DeploymentMode {

    /**
     * Trusted single-user: authentication optional. Requests run as an implicit
     * {@code local} principal with full control (SPEC "Use Product Without Login").
     */
    LOCAL,

    /**
     * Shared multi-user: authenticated via OAuth2/OIDC bearer JWTs. Workspace
     * content is blocked until authentication succeeds.
     */
    SHARED
}
