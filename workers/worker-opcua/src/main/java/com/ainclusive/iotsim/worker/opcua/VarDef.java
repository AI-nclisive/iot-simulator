package com.ainclusive.iotsim.worker.opcua;

/** A variable to expose in the OPC UA address space (from the neutral schema). */
record VarDef(String nodeId, String name, String dataType) {
}
