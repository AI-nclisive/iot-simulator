package com.ainclusive.iotsim.platform.capture;

import com.ainclusive.iotsim.platform.secret.ConnectionCredentials;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.util.List;

/**
 * What to capture: the protocol, the real endpoint to record from, the
 * session-only credentials, and the data-source schema (its VARIABLE nodes are
 * subscribed to, and their data types let the capturer decode observed values
 * neutrally). {@code credentials} are used in memory only and never persisted
 * (backend-specs/08). {@code protocol} is a plain string so this port stays free
 * of domain enums, mirroring {@link com.ainclusive.iotsim.platform.scan.ScanSpec}.
 */
public record CaptureSpec(String protocol, String endpointUrl,
        ConnectionCredentials credentials, int schemaVersion, List<SchemaNode> schemaNodes) {

    public CaptureSpec {
        schemaNodes = schemaNodes == null ? List.of() : List.copyOf(schemaNodes);
    }
}
