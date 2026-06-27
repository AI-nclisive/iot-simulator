package com.ainclusive.iotsim.api.support;

/**
 * Write-only connection credentials accepted on create/update and on scan. Never
 * returned in any response — only a {@code credentialState} or scan status is.
 * Secrets are held session-only and never persisted/exported (backend-specs/08).
 * Shared by the data-sources and scan surfaces.
 */
public record ConnectionConfigRequest(String mode, String username, String secret, String secretRef) {

    /** Redacted: never renders the secret in logs, test output, or exception messages. */
    @Override
    public String toString() {
        return "ConnectionConfigRequest[mode=" + mode
                + ", username=" + username
                + ", secret=" + (secret == null ? "null" : "***")
                + ", secretRef=" + secretRef + ']';
    }
}
