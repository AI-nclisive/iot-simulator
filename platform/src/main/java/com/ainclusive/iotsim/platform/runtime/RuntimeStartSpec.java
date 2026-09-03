package com.ainclusive.iotsim.platform.runtime;

import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.util.List;
import java.util.Map;

/**
 * Everything a worker needs to begin serving a data-source: protocol, the
 * protocol-neutral schema to project, and the protocol listen port (0 =
 * ephemeral). See openspec/specs/worker-contract/spec.md.
 */
public record RuntimeStartSpec(
        String protocol,
        int schemaVersion,
        List<SchemaNode> schemaNodes,
        int listenPort,
        DeterministicSettings deterministicSettings,
        EndpointSecurity endpointSecurity,
        Map<String, String> workerOptions) {

    public RuntimeStartSpec {
        schemaNodes = schemaNodes == null ? List.of() : List.copyOf(schemaNodes);
        endpointSecurity = endpointSecurity == null ? EndpointSecurity.none() : endpointSecurity;
        workerOptions = workerOptions == null ? Map.of() : Map.copyOf(workerOptions);
    }

    /** Convenience constructor for callers that do not supply deterministic settings. */
    public RuntimeStartSpec(String protocol, int schemaVersion, List<SchemaNode> schemaNodes, int listenPort) {
        this(protocol, schemaVersion, schemaNodes, listenPort, null, EndpointSecurity.none(), Map.of());
    }

    /** Convenience constructor without endpoint security (defaults to None/Anonymous). */
    public RuntimeStartSpec(String protocol, int schemaVersion, List<SchemaNode> schemaNodes, int listenPort,
            DeterministicSettings deterministicSettings) {
        this(protocol, schemaVersion, schemaNodes, listenPort, deterministicSettings, EndpointSecurity.none(), Map.of());
    }

    /** Convenience constructor without transport-specific worker options. */
    public RuntimeStartSpec(String protocol, int schemaVersion, List<SchemaNode> schemaNodes, int listenPort,
            DeterministicSettings deterministicSettings, EndpointSecurity endpointSecurity) {
        this(protocol, schemaVersion, schemaNodes, listenPort, deterministicSettings, endpointSecurity, Map.of());
    }
}
