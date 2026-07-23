package com.ainclusive.iotsim.protocolmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DataTypeMemberTest {

    @Test
    void constructsWithPrimitiveDataType() {
        var member = new DataTypeMember("x", DataType.FLOAT64, null);
        assertThat(member.dataType()).isEqualTo(DataType.FLOAT64);
        assertThat(member.dataTypeNodeId()).isNull();
    }

    @Test
    void constructsWithCustomTypeReference() {
        var member = new DataTypeMember("nested", null, "dt2");
        assertThat(member.dataTypeNodeId()).isEqualTo("dt2");
        assertThat(member.dataType()).isNull();
    }

    @Test
    void rejectsBlankName() {
        assertThatThrownBy(() -> new DataTypeMember(" ", DataType.FLOAT64, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void rejectsNeitherDataTypeNorTypeDefinitionSet() {
        assertThatThrownBy(() -> new DataTypeMember("x", null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void rejectsBothDataTypeAndTypeDefinitionSet() {
        assertThatThrownBy(() -> new DataTypeMember("x", DataType.FLOAT64, "dt2"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }
}
