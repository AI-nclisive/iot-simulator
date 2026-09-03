package com.ainclusive.iotsim.persistence.datasource;

import java.time.OffsetDateTime;

/** Persistence-level projection of a {@code data_sources} row. */
public record DataSourceRow(
        String id,
        String projectId,
        String name,
        String protocol,
        String basis,
        String schemaId,
        Integer schemaVersion,
        int simulatorPort,
        String realDeviceEndpoint,
        Integer realDeviceUnitId,
        String runtimeConfig,
        String securityConfig,
        boolean enabled,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        long version) {

    public DataSourceRow(String id, String projectId, String name, String protocol, String basis,
            String schemaId, Integer schemaVersion, int simulatorPort, String realDeviceEndpoint,
            String runtimeConfig, String securityConfig, boolean enabled, OffsetDateTime createdAt,
            OffsetDateTime updatedAt, String createdBy, long version) {
        this(id, projectId, name, protocol, basis, schemaId, schemaVersion, simulatorPort,
                realDeviceEndpoint, null, runtimeConfig, securityConfig, enabled, createdAt, updatedAt,
                createdBy, version);
    }
}
