package com.ainclusive.iotsim.protocolmodel;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import org.junit.jupiter.api.Test;

class NativeTypeDefinitionTest {

    @Test
    void rejectsUnsupportedTypeWithoutDiagnostic() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new NativeTypeCapability(false, true, true, null));
    }

    @Test
    void rejectsStructureWithoutFields() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new NativeTypeDefinition("range", null, "ns=0;i=884", "Range", "Range", null,
                        NativeTypeKind.STRUCTURE, null, null, null, List.of(), List.of(), null));
    }

    @Test
    void acceptsArrayFieldWithNativeTypeReference() {
        NativeTypeField field = new NativeTypeField("samples", null, "vendor:sample", ValueRank.ARRAY,
                List.of(0), false, null);
        new NativeTypeDefinition("batch", "urn:vendor", "ns=2;i=1001", "Batch", "Batch", null,
                NativeTypeKind.STRUCTURE, null, "ns=2;i=5001", null, List.of(field), List.of(),
                NativeTypeCapability.supported());
    }
}
