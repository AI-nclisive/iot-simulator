package com.ainclusive.iotsim.worker.opcua;

import java.util.Map;

/** Worker-local view of the endpoint's accepted user tokens (from the proto SecurityConfig). */
record AuthConfig(boolean anonymousAllowed, boolean usernameEnabled, Map<String, String> userPasswordHashes) {

    AuthConfig {
        userPasswordHashes = userPasswordHashes == null ? Map.of() : Map.copyOf(userPasswordHashes);
    }

    static AuthConfig anonymous() {
        return new AuthConfig(true, false, Map.of());
    }
}
