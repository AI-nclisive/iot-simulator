package com.epam.iotsim.protocolmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SchemaNodeTest {

    @Test
    void variableRequiresDataType() {
        assertThatThrownBy(() -> new SchemaNode(
                        "n1", null, "Plant/Temp", "Temp",
                        NodeKind.VARIABLE, null, ValueRank.SCALAR, Access.READ, "degC", null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("dataType");
    }

    @Test
    void folderDoesNotRequireDataType() {
        var folder = new SchemaNode(
                "f1", null, "Plant", "Plant",
                NodeKind.FOLDER, null, null, null, null, null);
        assertThat(folder.kind()).isEqualTo(NodeKind.FOLDER);
    }
}
