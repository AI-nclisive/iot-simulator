package com.ainclusive.iotsim.worker.opcua;

import com.ainclusive.iotsim.workercontract.v1.DataTypeEnumValueMsg;
import com.ainclusive.iotsim.workercontract.v1.DataTypeMemberMsg;
import java.util.List;

/** A schema-owned native OPC UA declaration projected by the worker. */
record NativeDataTypeDef(
        String nodeId,
        String name,
        List<DataTypeMemberMsg> members,
        List<DataTypeEnumValueMsg> enumValues,
        String defaultEncodingId,
        String nativeTypeKind) {

    NativeDataTypeDef {
        members = List.copyOf(members);
        enumValues = List.copyOf(enumValues);
    }

    boolean isEnum() {
        return !enumValues.isEmpty() && !isOptionSet();
    }

    boolean isOptionSet() {
        return "OPTION_SET".equals(nativeTypeKind);
    }

    boolean isStructure() {
        return !members.isEmpty();
    }

    boolean hasDefaultEncoding() {
        return defaultEncodingId != null && !defaultEncodingId.isBlank();
    }

    /** Compatibility constructor for declarations received before encoding metadata existed. */
    NativeDataTypeDef(String nodeId, String name, List<DataTypeMemberMsg> members,
            List<DataTypeEnumValueMsg> enumValues) {
        this(nodeId, name, members, enumValues, null, null);
    }
}
