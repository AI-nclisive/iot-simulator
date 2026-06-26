package com.ainclusive.iotsim.platform.secret;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Session-only {@link CredentialStore}: credentials live in process memory and
 * are never persisted, so they vanish on restart. This is the safe default
 * ({@code backend-specs/08_AUTH_AND_MODES.md}: connection secrets are used in
 * memory; persistence is optional and only via an external secret store).
 */
public final class InMemoryCredentialStore implements CredentialStore {

    private final ConcurrentMap<String, ConnectionCredentials> byHandle = new ConcurrentHashMap<>();

    @Override
    public void put(String handle, ConnectionCredentials credentials) {
        byHandle.put(Objects.requireNonNull(handle, "handle"), Objects.requireNonNull(credentials, "credentials"));
    }

    @Override
    public Optional<ConnectionCredentials> find(String handle) {
        return Optional.ofNullable(byHandle.get(handle));
    }

    @Override
    public boolean has(String handle) {
        return byHandle.containsKey(handle);
    }

    @Override
    public void clear(String handle) {
        byHandle.remove(handle);
    }
}
