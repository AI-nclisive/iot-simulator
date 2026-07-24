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

    @Test
    void rejectsMissingParentAndNonContainerParents() {
        SchemaNode orphan = node("child", "ghost", NodeKind.OBJECT);
        assertThatThrownBy(() -> SchemaNodeValidator.validate(List.of(orphan)))
                .hasMessageContaining("parent does not exist");

        SchemaNode method = node("method", null, NodeKind.METHOD);
        SchemaNode methodChild = node("method-child", "method", NodeKind.OBJECT);
        assertThatThrownBy(() -> SchemaNodeValidator.validate(List.of(method, methodChild)))
                .hasMessageContaining("parent must be a FOLDER, OBJECT, or VARIABLE");
    }

    @Test
    void acceptsVariableAsParentForPropertyChildren() {
        // IS-189: Variable can now be a parent for Property/Component children
        SchemaNode variable = new SchemaNode("sensor", null, "Sensor", "Sensor", NodeKind.VARIABLE,
                DataType.FLOAT64, ValueRank.SCALAR, Access.READ, null, null);
        SchemaNode euRange = new SchemaNode("eu_range", "sensor", "Sensor/EURange", "EURange",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, null, null, List.of(),
                null, List.of(new SchemaReference("sensor", ReferenceType.HAS_PROPERTY, false)));
        SchemaNode euUnits = new SchemaNode("eu_units", "sensor", "Sensor/EngineeringUnits", "EngineeringUnits",
                NodeKind.VARIABLE, DataType.STRING, ValueRank.SCALAR, Access.READ, null, null, List.of(),
                null, List.of(new SchemaReference("sensor", ReferenceType.HAS_PROPERTY, false)));

        SchemaNodeValidator.validate(List.of(variable, euRange, euUnits));
    }

    @Test
    void acceptsVariableReferencingCustomDataTypeAndNestedDataTypeMember() {
        SchemaNode primitiveStruct = dataTypeNode("dtPos", "Position",
                List.of(new DataTypeMember("x", DataType.FLOAT64, null),
                        new DataTypeMember("y", DataType.FLOAT64, null)));
        SchemaNode outer = dataTypeNode("dtBody", "Body",
                List.of(new DataTypeMember("position", null, "dtPos")));
        SchemaNode variable = new SchemaNode("v1", null, "v1", "Body1", NodeKind.VARIABLE,
                null, ValueRank.SCALAR, Access.READ, null, null, List.of(), null, List.of(), "dtBody", List.of());

        SchemaNodeValidator.validate(List.of(primitiveStruct, outer, variable));
    }

    @Test
    void rejectsVariableDataTypeNodeIdThatIsNotADataType() {
        SchemaNode object = node("machine", null, NodeKind.OBJECT);
        SchemaNode variable = new SchemaNode("v1", null, "v1", "V1", NodeKind.VARIABLE,
                null, ValueRank.SCALAR, Access.READ, null, null, List.of(), null, List.of(), "machine", List.of());

        assertThatThrownBy(() -> SchemaNodeValidator.validate(List.of(object, variable)))
                .hasMessageContaining("dataTypeNodeId target must be a DATA_TYPE node");
    }

    @Test
    void rejectsVariableDataTypeNodeIdThatDoesNotExist() {
        SchemaNode variable = new SchemaNode("v1", null, "v1", "V1", NodeKind.VARIABLE,
                null, ValueRank.SCALAR, Access.READ, null, null, List.of(), null, List.of(), "missing", List.of());

        assertThatThrownBy(() -> SchemaNodeValidator.validate(List.of(variable)))
                .hasMessageContaining("dataTypeNodeId target does not exist");
    }

    @Test
    void rejectsDuplicateMemberNames() {
        SchemaNode dataType = dataTypeNode("dt1", "Vector3D",
                List.of(new DataTypeMember("x", DataType.FLOAT64, null),
                        new DataTypeMember("x", DataType.FLOAT64, null)));

        assertThatThrownBy(() -> SchemaNodeValidator.validate(List.of(dataType)))
                .hasMessageContaining("duplicate member name");
    }

    @Test
    void rejectsMemberSelfReference() {
        SchemaNode dataType = dataTypeNode("dt1", "Vector3D",
                List.of(new DataTypeMember("self", null, "dt1")));

        assertThatThrownBy(() -> SchemaNodeValidator.validate(List.of(dataType)))
                .hasMessageContaining("cannot reference itself");
    }

    @Test
    void rejectsMemberTargetingNonDataTypeOrMissingNode() {
        SchemaNode object = node("machine", null, NodeKind.OBJECT);
        SchemaNode dataTypeWithBadMember =
                dataTypeNode("dt1", "Vector3D", List.of(new DataTypeMember("m", null, "machine")));

        assertThatThrownBy(() -> SchemaNodeValidator.validate(List.of(object, dataTypeWithBadMember)))
                .hasMessageContaining("must be a DATA_TYPE node");

        SchemaNode dataTypeWithMissingMember =
                dataTypeNode("dt2", "Vector3D", List.of(new DataTypeMember("m", null, "missing")));

        assertThatThrownBy(() -> SchemaNodeValidator.validate(List.of(dataTypeWithMissingMember)))
                .hasMessageContaining("dataTypeNodeId does not exist");
    }

    @Test
    void rejectsNestingADataTypeThatItselfNestsAnotherCustomType() {
        SchemaNode leaf = dataTypeNode("dtLeaf", "Leaf", List.of(new DataTypeMember("v", DataType.FLOAT64, null)));
        SchemaNode middle = dataTypeNode("dtMiddle", "Middle", List.of(new DataTypeMember("leaf", null, "dtLeaf")));
        SchemaNode outer = dataTypeNode("dtOuter", "Outer", List.of(new DataTypeMember("middle", null, "dtMiddle")));

        assertThatThrownBy(() -> SchemaNodeValidator.validate(List.of(leaf, middle, outer)))
                .hasMessageContaining("only one level of");
    }

    private static SchemaNode node(String id, String parentId, NodeKind kind) {
        return new SchemaNode(id, parentId, id, id, kind, null, null, null, null, null);
    }

    private static SchemaNode dataTypeNode(String id, String name, List<DataTypeMember> members) {
        return new SchemaNode(id, null, id, name, NodeKind.DATA_TYPE,
                null, null, null, null, null, List.of(), null, List.of(), null, members);
    }
}
