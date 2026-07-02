package com.ainclusive.iotsim.platform.runtime;

import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.util.List;

/**
 * Everything a worker needs to begin serving a data-source: protocol, the
 * protocol-neutral schema to project, and the protocol listen port (0 =
 * ephemeral). See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
public record RuntimeStartSpec(
        String protocol,
        int schemaVersion,
        List<SchemaNode> schemaNodes,
        int listenPort,
        DeterministicSettings deterministicSettings,
        EndpointSecurity endpointSecurity) {

    public RuntimeStartSpec {
        schemaNodes = schemaNodes == null ? List.of() : List.copyOf(schemaNodes);
        endpointSecurity = endpointSecurity == null ? EndpointSecurity.none() : endpointSecurity;
    }

    /** Convenience constructor for callers that do not supply deterministic settings. */
    public RuntimeStartSpec(String protocol, int schemaVersion, List<SchemaNode> schemaNodes, int listenPort) {
        this(protocol, schemaVersion, schemaNodes, listenPort, null, EndpointSecurity.none());
    }

    /** Convenience constructor without endpoint security (defaults to None/Anonymous). */
    public RuntimeStartSpec(String protocol, int schemaVersion, List<SchemaNode> schemaNodes, int listenPort,
            DeterministicSettings deterministicSettings) {
        this(protocol, schemaVersion, schemaNodes, listenPort, deterministicSettings, EndpointSecurity.none());
    }
}
