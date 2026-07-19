package com.ainclusive.iotsim.protocolmodel;

/**
 * Protocol-neutral primitive data types — a superset a protocol worker draws
 * from, not an intersection every protocol must fill. A schema/recording is
 * always scoped to a single protocol (see {@code ReplayGuards}), so a value
 * only one protocol produces (e.g. OPC UA's {@code LOCALIZED_TEXT}) is simply
 * never referenced by other protocols' workers — harmless, not a correctness
 * problem. Each worker's {@code XxxTypes} mapper (e.g.
 * {@code OpcUaTypes.neutralTypeOf}) reverse-maps its own native types onto
 * whichever subset of this enum it needs; a native type not worth promoting to
 * a first-class entry here stays "unknown" for the user to resolve or exclude
 * (IS-044).
 *
 * <p>See {@code backend-specs/01_PROTOCOL_NEUTRAL_MODEL.md} §2.
 */
public enum DataType {
    BOOL,
    INT8,
    UINT8,
    INT16,
    UINT16,
    INT32,
    UINT32,
    INT64,
    UINT64,
    FLOAT32,
    FLOAT64,
    STRING,
    BYTES,
    DATETIME,
    LOCALIZED_TEXT,
    GUID,
    STATUS_CODE,
    QUALIFIED_NAME,
    NODE_ID,
    EXPANDED_NODE_ID,
    XML_ELEMENT
}
