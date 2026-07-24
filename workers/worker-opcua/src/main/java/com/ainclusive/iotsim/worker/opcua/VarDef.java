package com.ainclusive.iotsim.worker.opcua;

/**
 * A schema node to expose in the OPC UA address space (from the neutral schema).
 *
 * @param referenceType the hierarchical reference from {@code parentId} to this node
 *     ({@code null} = the default {@code Organizes}, what every real simulated source
 *     uses). Test-only seam (e.g. {@code OpcUaDiscoveryIT}) for building fixtures that
 *     mimic a real device's {@code HasProperty} children — the simulator's own served
 *     address space never needs anything but {@code Organizes}.
 * @param accessLevelFull IS-189: Full 8-bit AccessLevel mask per IEC 62541
 * @param minimumSamplingInterval IS-189: Server minimum sampling interval in milliseconds
 * @param writeMask IS-189: Attribute write permissions
 * @param historizing IS-189: Whether server collects historical values
 */
record VarDef(String nodeId, String parentId, String name, String kind, String dataType, String referenceType,
        Integer accessLevelFull, Integer minimumSamplingInterval, Integer writeMask, Boolean historizing) {
    VarDef(String nodeId, String parentId, String name, String kind, String dataType, String referenceType) {
        this(nodeId, parentId, name, kind, dataType, referenceType, null, null, null, null);
    }

    VarDef(String nodeId, String parentId, String name, String kind, String dataType) {
        this(nodeId, parentId, name, kind, dataType, null, null, null, null, null);
    }

    VarDef(String nodeId, String name, String dataType) {
        this(nodeId, null, name, "VARIABLE", dataType, null, null, null, null, null);
    }
}
