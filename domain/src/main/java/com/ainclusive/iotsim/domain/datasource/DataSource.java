package com.ainclusive.iotsim.domain.datasource;

import java.time.Instant;

/**
 * A simulated instrument source. {@code runtimeState} is derived from the
 * supervisor and not persisted; {@code credentialState} is derived from the
 * credential store and never carries the secret itself. See
 * backend-specs/03_DOMAIN_MODEL.md.
 */
public record DataSource(
        String id,
        String projectId,
        String name,
        Protocol protocol,
        SourceBasis basis,
        String schemaId,
        Integer schemaVersion,
        String endpoint,
        String runtimeConfig,
        boolean enabled,
        RuntimeState runtimeState,
        CredentialState credentialState,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        long version) {
}
