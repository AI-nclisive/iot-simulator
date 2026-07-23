package com.ainclusive.iotsim.api.schema;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.protocolmodel.DataType;
import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaReferenceMapperTest {

    @Test
    void toMembersParsesPrimitiveDataTypeAndCustomReference() {
        var members = SchemaReferenceMapper.toMembers(List.of(
                new MemberDto("x", "FLOAT64", null),
                new MemberDto("nested", null, "dt2")));

        assertThat(members).hasSize(2);
        assertThat(members.get(0).dataType()).isEqualTo(DataType.FLOAT64);
        assertThat(members.get(1).dataTypeNodeId()).isEqualTo("dt2");
    }

    @Test
    void toMembersRejectsInvalidDataTypeWithAClearMessage() {
        assertThatThrownBy(() -> SchemaReferenceMapper.toMembers(List.of(new MemberDto("x", "NOT_A_TYPE", null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid dataType: NOT_A_TYPE");
    }
}
