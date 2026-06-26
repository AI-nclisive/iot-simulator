package com.ainclusive.iotsim.platform.secret;

import java.util.Objects;

/**
 * Connection secrets used to scan or record a real data source.
 *
 * <p>Per {@code backend-specs/08_AUTH_AND_MODES.md} these are used in memory
 * only: they must never appear in entity rows, exports, evidence, activity, or
 * summaries. {@link #toString()} is redacted so a secret cannot leak through
 * logs or error messages.
 */
public record ConnectionCredentials(Mode mode, String username, String secret, String secretRef) {

    /** How the connection authenticates. */
    public enum Mode {
        /** No credentials; the source is reached anonymously. */
        ANONYMOUS,
        /** Session-only username/password held in memory. */
        PASSWORD,
        /** Reference resolved from an external secret store; the reference is not itself a secret. */
        EXTERNAL_REF
    }

    public ConnectionCredentials {
        Objects.requireNonNull(mode, "mode");
        switch (mode) {
            case ANONYMOUS -> {
                username = null;
                secret = null;
                secretRef = null;
            }
            case PASSWORD -> {
                if (secret == null || secret.isBlank()) {
                    throw new IllegalArgumentException("PASSWORD credentials require a secret");
                }
                secretRef = null;
            }
            case EXTERNAL_REF -> {
                if (secretRef == null || secretRef.isBlank()) {
                    throw new IllegalArgumentException("EXTERNAL_REF credentials require a secretRef");
                }
                username = null;
                secret = null;
            }
            default -> throw new IllegalArgumentException("unsupported mode: " + mode);
        }
    }

    /** No credentials. */
    public static ConnectionCredentials anonymous() {
        return new ConnectionCredentials(Mode.ANONYMOUS, null, null, null);
    }

    /** Session-only username/password. */
    public static ConnectionCredentials password(String username, String secret) {
        return new ConnectionCredentials(Mode.PASSWORD, username, secret, null);
    }

    /** A reference resolved from an external secret store. */
    public static ConnectionCredentials externalRef(String secretRef) {
        return new ConnectionCredentials(Mode.EXTERNAL_REF, null, null, secretRef);
    }

    /** True when these carry a secret value that must never be persisted or exported. */
    public boolean hasSecret() {
        return mode == Mode.PASSWORD;
    }

    /** Redacted: never reveals the secret in logs, summaries, or error messages. */
    @Override
    public String toString() {
        return "ConnectionCredentials[mode=" + mode
                + ", username=" + (username == null ? "null" : '"' + username + '"')
                + ", secret=" + (secret == null ? "null" : "***")
                + ", secretRef=" + (secretRef == null ? "null" : '"' + secretRef + '"')
                + ']';
    }
}
