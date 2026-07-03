package com.ainclusive.iotsim.protocolmodel;

/** Controls what a recording captures: schema nodes only, or schema nodes + value timeline. */
public enum ScanType {
    SCHEMA_ONLY,
    SCHEMA_AND_DATA,
}
