package com.ainclusive.iotsim.worker.opcua;

import static org.eclipse.milo.opcua.stack.core.types.builtin.unsigned.Unsigned.uint;

import java.util.concurrent.TimeUnit;
import org.eclipse.milo.opcua.sdk.client.OpcUaClient;
import org.eclipse.milo.opcua.sdk.client.api.identity.AnonymousProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.IdentityProvider;
import org.eclipse.milo.opcua.sdk.client.api.identity.UsernameProvider;
import org.eclipse.milo.opcua.stack.core.StatusCodes;
import org.eclipse.milo.opcua.stack.core.UaException;
import org.eclipse.milo.opcua.stack.core.security.SecurityPolicy;
import org.eclipse.milo.opcua.stack.core.types.builtin.LocalizedText;

/**
 * Shared OPC UA <em>client-mode</em> plumbing for the worker's real-source
 * operations — discovery ({@link OpcUaDiscovery}, IS-043) and live capture
 * ({@link OpcUaCapture}, IS-045): connect/authenticate against a real endpoint,
 * tear the client down, and classify failures. Kept in one place so both paths
 * stay consistent. None security / session-only credentials, per
 * backend-specs/02 §6 and 08.
 */
final class OpcUaClientSupport {

    static final String OK = "OK";
    static final String UNREACHABLE = "UNREACHABLE";
    static final String AUTH_FAILURE = "AUTH_FAILURE";

    private static final long CONNECT_TIMEOUT_SECONDS = 15;
    private static final long DISCONNECT_TIMEOUT_SECONDS = 10;
    private static final int REQUEST_TIMEOUT_MILLIS = 10_000;

    private OpcUaClientSupport() {}

    /**
     * Connects to a real endpoint (None security) and authenticates per the
     * session-only credentials. {@code mode} is {@code ANONYMOUS}, {@code PASSWORD}
     * or {@code EXTERNAL_REF}; only PASSWORD carries a secret (EXTERNAL_REF cannot
     * be resolved in the worker yet — IS-082 — so it falls back to anonymous).
     */
    static OpcUaClient connect(String endpointUrl, String mode, String username, String secret)
            throws Exception {
        OpcUaClient client = OpcUaClient.create(
                endpointUrl,
                endpoints -> endpoints.stream()
                        .filter(e -> SecurityPolicy.None.getUri().equals(e.getSecurityPolicyUri()))
                        .findFirst()
                        .or(() -> endpoints.stream().findFirst()),
                cfg -> cfg
                        .setApplicationName(LocalizedText.english("IoT Simulator Client"))
                        .setApplicationUri("urn:iotsim:opcua:client")
                        .setIdentityProvider(identityProvider(mode, username, secret))
                        .setRequestTimeout(uint(REQUEST_TIMEOUT_MILLIS))
                        .build());
        client.connect().get(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return client;
    }

    private static IdentityProvider identityProvider(String mode, String username, String secret) {
        if ("PASSWORD".equals(mode)) {
            return new UsernameProvider(username == null ? "" : username, secret == null ? "" : secret);
        }
        // ANONYMOUS, or EXTERNAL_REF which cannot be resolved here yet (IS-082).
        return new AnonymousProvider();
    }

    static void disconnectQuietly(OpcUaClient client) {
        if (client != null) {
            try {
                client.disconnect().get(DISCONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // best effort; we are tearing down a client-mode session
            }
        }
    }

    /**
     * Classifies a failure by unwrapping the cause chain to the underlying
     * {@link UaException}. Authentication/authorization status codes map to
     * {@code AUTH_FAILURE}; anything else (server down, timeout, refused) is
     * {@code UNREACHABLE}.
     */
    static String classify(Throwable t) {
        for (Throwable c = t; c != null; c = (c.getCause() == c ? null : c.getCause())) {
            if (c instanceof UaException ua) {
                long code = ua.getStatusCode().getValue();
                if (code == StatusCodes.Bad_UserAccessDenied
                        || code == StatusCodes.Bad_IdentityTokenInvalid
                        || code == StatusCodes.Bad_IdentityTokenRejected) {
                    return AUTH_FAILURE;
                }
                return UNREACHABLE;
            }
        }
        return UNREACHABLE;
    }

    static void reinterruptIfNeeded(Throwable t) {
        if (t instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
    }

    static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        if (cause instanceof UaException ua) {
            return ua.getMessage();
        }
        String message = cause.getMessage();
        return message == null ? cause.getClass().getSimpleName() : message;
    }
}
