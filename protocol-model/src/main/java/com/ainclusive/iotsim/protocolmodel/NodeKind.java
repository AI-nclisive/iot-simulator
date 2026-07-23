package com.ainclusive.iotsim.protocolmodel;

/**
 * Schema node kind. FOLDER is retained for schemas created before IS-176.
 *
 * <p>{@code DATA_TYPE} (IS-183) is a user-authored custom structured type definition (OPC UA's
 * {@code DataType} NodeClass — e.g. a "Vector3D" struct with x/y/z FLOAT64 members). It is not
 * part of the FOLDER/OBJECT parent-child hierarchy: it is a top-level type definition that is
 * referenced (by a VARIABLE's {@code typeDefinition}, or by another DATA_TYPE's member) rather
 * than nested under a parent — see {@link SchemaNodeValidator}.
 */
public enum NodeKind {
    FOLDER,
    OBJECT,
    VARIABLE,
    METHOD,
    DATA_TYPE
}
