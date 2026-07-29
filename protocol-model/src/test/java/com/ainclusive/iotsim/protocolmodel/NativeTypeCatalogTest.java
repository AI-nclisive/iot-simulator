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
}
