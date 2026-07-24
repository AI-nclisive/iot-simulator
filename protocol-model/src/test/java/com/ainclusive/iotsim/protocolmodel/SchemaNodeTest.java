package com.ainclusive.iotsim.protocolmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SchemaNodeTest {

    @Test
    void variableRequiresDataType() {
        assertThatThrownBy(() -> new SchemaNode(
                        "n1", null, "Plant/Temp", "Temp",
                        NodeKind.VARIABLE, null, ValueRank.SCALAR, Access.READ, "degC", null,
                        java.util.List.of(), null, java.util.List.of(), null, java.util.List.of(),
                        null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataType");
    }

    @Test
    void folderDoesNotRequireDataType() {
        var folder = new SchemaNode(
                "f1", null, "Plant", "Plant",
                NodeKind.FOLDER, null, null, null, null, null,
                java.util.List.of(), null, java.util.List.of(), null, java.util.List.of(),
                null, null, null, null);
        assertThat(folder.kind()).isEqualTo(NodeKind.FOLDER);
    }

    @Test
    void variableMayReferenceCustomDataTypeInsteadOfPrimitive() {
        var variable = new SchemaNode(
                "n1", null, "Plant/Vec", "Vec", NodeKind.VARIABLE, null, ValueRank.SCALAR, Access.READ,
                null, null, java.util.List.of(), null, java.util.List.of(), "dt1", java.util.List.of(),
                null, null, null, null);
        assertThat(variable.dataType()).isNull();
        assertThat(variable.dataTypeNodeId()).isEqualTo("dt1");
    }

    @Test
    void variableRejectsBothDataTypeAndDataTypeNodeId() {
        assertThatThrownBy(() -> new SchemaNode(
                        "n1", null, "Plant/Vec", "Vec", NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR,
                        Access.READ, null, null, java.util.List.of(), null, java.util.List.of(), "dt1",
                        java.util.List.of(), null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void dataTypeNodeRequiresAtLeastOneMemberAndNoParent() {
        assertThatThrownBy(() -> new SchemaNode(
                        "dt1", null, "Vector3D", "Vector3D", NodeKind.DATA_TYPE, null, null, null, null, null,
                        java.util.List.of(), null, java.util.List.of(), null, java.util.List.of(),
                        null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one member");

        assertThatThrownBy(() -> new SchemaNode(
                        "dt1", "someParent", "Vector3D", "Vector3D", NodeKind.DATA_TYPE, null, null, null, null,
                        null, java.util.List.of(), null, java.util.List.of(), null,
                        java.util.List.of(new DataTypeMember("x", DataType.FLOAT64, null)),
                        null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("top-level");
    }

    @Test
    void dataTypeNodeWithMembersConstructs() {
        var dataType = new SchemaNode(
                "dt1", null, "Vector3D", "Vector3D", NodeKind.DATA_TYPE, null, null, null, null, null,
                java.util.List.of(), null, java.util.List.of(), null,
                java.util.List.of(
                        new DataTypeMember("x", DataType.FLOAT64, null),
                        new DataTypeMember("y", DataType.FLOAT64, null),
                        new DataTypeMember("z", DataType.FLOAT64, null)),
                null, null, null, null);
        assertThat(dataType.members()).hasSize(3);
    }

    @Test
    void dataTypeNodeRejectsANonNullDataTypeField() {
        assertThatThrownBy(() -> new SchemaNode(
                        "dt1", null, "Vector3D", "Vector3D", NodeKind.DATA_TYPE, DataType.FLOAT64, null, null,
                        null, null, java.util.List.of(), null, java.util.List.of(), null,
                        java.util.List.of(new DataTypeMember("x", DataType.FLOAT64, null)),
                        null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot have a dataType field");
    }

    @Test
    void nonDataTypeNodeRejectsMembers() {
        assertThatThrownBy(() -> new SchemaNode(
                        "f1", null, "Plant", "Plant", NodeKind.FOLDER, null, null, null, null, null,
                        java.util.List.of(), null, java.util.List.of(), null,
                        java.util.List.of(new DataTypeMember("x", DataType.FLOAT64, null)),
                        null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot have members");
    }
}
