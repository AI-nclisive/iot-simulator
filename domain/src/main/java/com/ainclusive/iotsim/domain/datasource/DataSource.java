package com.ainclusive.iotsim.domain.datasource;

import java.time.Instant;

/**
 * A simulated instrument source. {@code runtimeState} is derived from the
 * supervisor and not persisted; {@code credentialState} is derived from the
 * credential store and never carries the secret itself. {@code serveUrl} is the
 * advertised endpoint a client connects to, derived from the protocol, the
 * configured {@code advertisedHost}, and {@code simulatorPort}.
 * See backend-specs/03_DOMAIN_MODEL.md.
 */
public record DataSource(
        String id,
        String projectId,
        String name,
        Protocol protocol,
        SourceBasis basis,
        String schemaId,
        Integer schemaVersion,
        int simulatorPort,
        String realDeviceEndpoint,
        String runtimeConfig,
        boolean enabled,
        RuntimeState runtimeState,
        CredentialState credentialState,
        String serveUrl,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        long version) {
}
