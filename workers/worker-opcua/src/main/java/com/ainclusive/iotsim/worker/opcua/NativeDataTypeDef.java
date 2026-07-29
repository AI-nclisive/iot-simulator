package com.ainclusive.iotsim.worker.opcua;

import com.ainclusive.iotsim.workercontract.v1.DataTypeEnumValueMsg;
import com.ainclusive.iotsim.workercontract.v1.DataTypeMemberMsg;
import java.util.List;

/** A schema-owned native OPC UA declaration projected by the worker. */
record NativeDataTypeDef(
        String nodeId,
        String name,
        List<DataTypeMemberMsg> members,
        List<DataTypeEnumValueMsg> enumValues) {

    NativeDataTypeDef {
        members = List.copyOf(members);
        enumValues = List.copyOf(enumValues);
    }

    boolean isEnum() {
        return !enumValues.isEmpty();
    }
}
