package com.ainclusive.iotsim.api.schema;

import com.ainclusive.iotsim.protocolmodel.DataTypeMember;

/**
 * REST representation of one member of a {@code DATA_TYPE} node's structure (IS-183). Exactly one
 * of {@code dataType} (a primitive, e.g. {@code "FLOAT64"}) or {@code typeDefinitionNodeId} (the
 * {@code nodeId} of another DATA_TYPE node) is set.
 */
public record MemberDto(String name, String dataType, String dataTypeNodeId) {
    public static MemberDto from(DataTypeMember member) {
        return new MemberDto(member.name(),
                member.dataType() == null ? null : member.dataType().name(),
                member.dataTypeNodeId());
    }
}
