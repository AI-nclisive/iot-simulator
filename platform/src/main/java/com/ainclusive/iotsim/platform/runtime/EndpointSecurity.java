package com.ainclusive.iotsim.platform.runtime;

import java.util.List;

/**
 * Neutral, per-source OPC UA endpoint security the worker enforces. Phase 1
 * (IS-131) carries user-token policy only; message security is added in IS-132.
 * {@link #none()} reproduces the historical None/Anonymous server.
 */
public record EndpointSecurity(boolean anonymousAllowed, boolean usernameEnabled, List<UserCredential> users) {

    public EndpointSecurity {
        users = users == null ? List.of() : List.copyOf(users);
    }

    public static EndpointSecurity none() {
        return new EndpointSecurity(true, false, List.of());
    }

    /** A username the server accepts and the salted hash of its password. */
    public record UserCredential(String username, String passwordHash) {}
}
