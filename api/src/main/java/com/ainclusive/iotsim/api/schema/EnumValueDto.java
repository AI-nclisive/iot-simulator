package com.ainclusive.iotsim.api.schema;

import com.ainclusive.iotsim.protocolmodel.DataTypeEnumValue;

/** REST representation of one numeric literal in an enum or option-set DATA_TYPE. */
public record EnumValueDto(String name, long value, String description) {
    public static EnumValueDto from(DataTypeEnumValue value) {
        return new EnumValueDto(value.name(), value.value(), value.description());
    }

    public DataTypeEnumValue toModel() {
        return new DataTypeEnumValue(name, value, description);
    }
}
