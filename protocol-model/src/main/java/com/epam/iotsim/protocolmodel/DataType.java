package com.epam.iotsim.protocolmodel;

/**
 * Protocol-neutral primitive data types. Chosen as the intersection that maps
 * cleanly to both OPC UA built-in types and Modbus registers.
 *
 * <p>See {@code backend-specs/01_PROTOCOL_NEUTRAL_MODEL.md} §2.
 */
public enum DataType {
    BOOL,
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
    DATETIME
}
