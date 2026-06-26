package com.ainclusive.iotsim.domain.datasource;

/**
 * Whether a data source has connection credentials, and how durable they are.
 * Never carries the secret itself — only its presence is surfaced, so the
 * Credential Handling surface can show masked / session-only state
 * (frontend/docs/UI_SCREEN_SPECS.md).
 */
public enum CredentialState {
    /** No credentials configured. */
    MISSING,
    /** Credentials held in memory for this process only; lost on restart. */
    SESSION_ONLY
}
