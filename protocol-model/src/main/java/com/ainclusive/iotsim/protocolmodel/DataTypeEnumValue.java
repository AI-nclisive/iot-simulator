package com.ainclusive.iotsim.protocolmodel;

import java.util.Objects;

/** One declared numeric literal of an OPC UA enum or option-set DataType. */
public record DataTypeEnumValue(String name, long value, String description) {
    public DataTypeEnumValue {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("enum value name must not be blank");
        }
    }
}
