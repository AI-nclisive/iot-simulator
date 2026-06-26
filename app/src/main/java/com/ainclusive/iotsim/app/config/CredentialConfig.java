package com.ainclusive.iotsim.app.config;

import com.ainclusive.iotsim.platform.secret.CredentialStore;
import com.ainclusive.iotsim.platform.secret.InMemoryCredentialStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link CredentialStore}. The default is session-only (in memory):
 * connection secrets live for the current process only and are never persisted
 * (backend-specs/08_AUTH_AND_MODES.md). A persistent, external-secret-store
 * adapter can be selected here later without touching the domain.
 */
@Configuration
public class CredentialConfig {

    @Bean
    public CredentialStore credentialStore() {
        return new InMemoryCredentialStore();
    }
}
