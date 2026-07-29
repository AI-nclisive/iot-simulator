package com.ainclusive.iotsim.protocolmodel;

import java.util.List;
import java.util.Objects;

/** One ordered field in a native OPC UA structure or union declaration. */
public record NativeTypeField(
        String name,
        DataType dataType,
        String dataTypeId,
        ValueRank valueRank,
        List<Integer> arrayDimensions,
        boolean optional,
        Integer unionSwitchValue) {

    public NativeTypeField {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("native type field name must not be blank");
        }
        if ((dataType == null) == (dataTypeId == null)) {
            throw new IllegalArgumentException(
                    "native type field '" + name + "' requires exactly one of dataType or dataTypeId");
        }
        valueRank = valueRank == null ? ValueRank.SCALAR : valueRank;
        arrayDimensions = arrayDimensions == null ? List.of() : List.copyOf(arrayDimensions);
        if (valueRank == ValueRank.SCALAR && !arrayDimensions.isEmpty()) {
            throw new IllegalArgumentException("arrayDimensions require ARRAY valueRank");
        }
        if (arrayDimensions.stream().anyMatch(dimension -> dimension == null || dimension < 0)) {
            throw new IllegalArgumentException("arrayDimensions must be non-negative");
        }
    }
}
