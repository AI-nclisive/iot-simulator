package com.ainclusive.iotsim.platform.scan;

import com.ainclusive.iotsim.platform.secret.ConnectionCredentials;

/**
 * What to scan: the protocol, the real endpoint, the session-only credentials, and
 * a cap on discovered nodes. {@code credentials} are used in memory for the scan
 * only and are never persisted (backend-specs/08).
 *
 * <p>{@code protocol} is a plain string (e.g. {@code OPC_UA}) so this port stays
 * free of domain enums, mirroring {@code RuntimeController}.
 */
public record ScanSpec(String protocol, String endpointUrl,
        ConnectionCredentials credentials, int maxNodes) {
}
