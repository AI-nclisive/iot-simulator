package com.ainclusive.iotsim.platform.secret;

import java.util.Optional;

/**
 * Stores {@link ConnectionCredentials} for a data source, keyed by an opaque
 * handle (the data-source id).
 *
 * <p>The default adapter keeps credentials in process memory only
 * (session-only). A persistent adapter, when added, must store secrets via an
 * external secret store and never inline in entity rows, exports, evidence,
 * activity, or summaries — see {@code backend-specs/08_AUTH_AND_MODES.md}.
 */
public interface CredentialStore {

    /** Stores credentials for the handle, replacing any already held. */
    void put(String handle, ConnectionCredentials credentials);

    /** Returns the credentials held for the handle, if any. */
    Optional<ConnectionCredentials> find(String handle);

    /** True when credentials are held for the handle. */
    boolean has(String handle);

    /** Removes any credentials held for the handle. */
    void clear(String handle);
}
