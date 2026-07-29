package com.ainclusive.iotsim.protocolmodel;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class NativeTypeCatalogTest {

    @Test
    void exposesExistingEnumAndStructureDeclarationsAsCatalogEntries() {
        SchemaNode range = new SchemaNode("ns=0;i=884", null, "Types/Range", "Range", NodeKind.DATA_TYPE,
                null, null, null, null, "Engineering range", List.of(), null, List.of(), null,
                List.of(new DataTypeMember("low", DataType.FLOAT64, null),
                        new DataTypeMember("high", DataType.FLOAT64, null)), List.of(), "ns=0;i=886",
                null, null, null, null);
        SchemaNode mode = new SchemaNode("mode", null, "Types/Mode", "Mode", NodeKind.DATA_TYPE,
                null, null, null, null, null, List.of(), null, List.of(), null, List.of(),
                List.of(new DataTypeEnumValue("Off", 0, ""), new DataTypeEnumValue("On", 1, "")),
                null, null, null, null, null);

        List<NativeTypeDefinition> entries = NativeTypeCatalog.fromSchemaNodes(List.of(range, mode));

        assertThat(entries).extracting(NativeTypeDefinition::kind)
                .containsExactly(NativeTypeKind.STRUCTURE, NativeTypeKind.ENUM);
        assertThat(entries.getFirst().fields()).hasSize(2);
        assertThat(entries.getFirst().capability().replayEncodable()).isTrue();
    }

    @Test
    void exposesDefinitionlessDataTypeAsOpaqueAndUnsupported() {
        SchemaNode opaque = new SchemaNode("ns=2;i=2002", null, "Types/ns=2;i=2002", "ns=2;i=2002",
                NodeKind.DATA_TYPE, null, null, null, null, "source omitted declaration", List.of(), null,
                List.of(), null, List.of(), List.of(), null, null, null, null, null);

        NativeTypeDefinition entry = NativeTypeCatalog.fromSchemaNodes(List.of(opaque)).getFirst();

        assertThat(entry.kind()).isEqualTo(NativeTypeKind.OPAQUE);
        assertThat(entry.capability().materializable()).isFalse();
    }

    @Test
    void retainsExplicitUnionTypeKind() {
        SchemaNode union = new SchemaNode("choice", null, "Types/Choice", "Choice", NodeKind.DATA_TYPE,
                null, null, null, null, null, List.of(), null, List.of(), null,
                List.of(new DataTypeMember("integer", DataType.INT32, null)), List.of(), "ns=2;i=5002",
                NativeTypeKind.UNION, null, null, null, null);

        assertThat(NativeTypeCatalog.fromSchemaNodes(List.of(union)).getFirst().kind())
                .isEqualTo(NativeTypeKind.UNION);
    }
}
