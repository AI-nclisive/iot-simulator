package com.ainclusive.iotsim.worker.opcua;

/** A schema node to expose in the OPC UA address space (from the neutral schema). */
record VarDef(String nodeId, String parentId, String name, String kind, String dataType) {
    VarDef(String nodeId, String name, String dataType) {
        this(nodeId, null, name, "VARIABLE", dataType);
    }
}
