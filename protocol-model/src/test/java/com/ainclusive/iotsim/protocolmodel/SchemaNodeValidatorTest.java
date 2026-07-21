package com.ainclusive.iotsim.protocolmodel;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class SchemaNodeValidatorTest {

    @Test
    void acceptsObjectMethodArrayAndTypedReference() {
        SchemaNode object = node("machine", null, NodeKind.OBJECT);
        SchemaNode variable = new SchemaNode("samples", "machine", "Machine/Samples", "Samples",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.ARRAY, Access.READ, null, null,
                List.of(16), "ns=0;i=63", List.of(new SchemaReference("machine", ReferenceType.ORGANIZES, false)));
        SchemaNode method = node("reset", "machine", NodeKind.METHOD);

        SchemaNodeValidator.validate(List.of(object, variable, method));
    }

    @Test
    void rejectsCyclicParentsAndUnknownReferenceTargets() {
        SchemaNode first = node("first", "second", NodeKind.OBJECT);
        SchemaNode second = node("second", "first", NodeKind.OBJECT);
        assertThatThrownBy(() -> SchemaNodeValidator.validate(List.of(first, second)))
                .hasMessageContaining("cyclic parent");

        SchemaNode danglingReference = new SchemaNode("object", null, "Object", "Object", NodeKind.OBJECT,
                null, null, null, null, null, List.of(), null,
                List.of(new SchemaReference("missing", ReferenceType.HAS_COMPONENT, true)));
        assertThatThrownBy(() -> SchemaNodeValidator.validate(List.of(danglingReference)))
                .hasMessageContaining("reference target does not exist");
    }

    private static SchemaNode node(String id, String parentId, NodeKind kind) {
        return new SchemaNode(id, parentId, id, id, kind, null, null, null, null, null);
    }
}
