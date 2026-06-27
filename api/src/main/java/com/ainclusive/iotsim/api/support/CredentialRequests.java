package com.ainclusive.iotsim.api.support;

import com.ainclusive.iotsim.platform.secret.ConnectionCredentials;
import java.util.Locale;

/**
 * Maps the write-only {@link ConnectionConfigRequest} to a
 * {@link ConnectionCredentials}. The mode string mirrors the UI
 * (anonymous / password / external-ref, case-insensitive). Secrets are never
 * echoed back.
 */
public final class CredentialRequests {

    private CredentialRequests() {}

    /** {@code null} means "leave unchanged"; an empty/anonymous mode means no credentials. */
    public static ConnectionCredentials toCredentials(ConnectionConfigRequest cfg) {
        if (cfg == null) {
            return null;
        }
        String mode = (cfg.mode() == null ? "" : cfg.mode().trim().toLowerCase(Locale.ROOT)).replace('-', '_');
        return switch (mode) {
            case "", "anonymous" -> ConnectionCredentials.anonymous();
            case "password" -> ConnectionCredentials.password(cfg.username(), cfg.secret());
            case "external_ref" -> ConnectionCredentials.externalRef(cfg.secretRef());
            default -> throw new IllegalArgumentException("unknown credential mode: " + cfg.mode());
        };
    }
}
