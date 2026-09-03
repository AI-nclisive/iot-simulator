package com.ainclusive.iotsim.protocolmodel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

/** IS-060: the optional explicit Modbus register-map override on a VARIABLE node. */
class SchemaNodeModbusBindingTest {

    @Test
    void bindingIsOptionalAndDefaultsToNull() {
        SchemaNode node = variable(null, null);
        assertThat(node.modbusRegisterKind()).isNull();
        assertThat(node.modbusAddress()).isNull();
    }

    @Test
    void bindingCanBeSetTogether() {
        SchemaNode node = variable("HOLDING_REGISTER", 1000);
        assertThat(node.modbusRegisterKind()).isEqualTo("HOLDING_REGISTER");
        assertThat(node.modbusAddress()).isEqualTo(1000);
    }

    @Test
    void registerKindWithoutAddressIsRejected() {
        assertThatThrownBy(() -> variable("HOLDING_REGISTER", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("together");
    }

    @Test
    void addressWithoutRegisterKindIsRejected() {
        assertThatThrownBy(() -> variable(null, 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("together");
    }

    @Test
    void negativeAddressIsRejected() {
        assertThatThrownBy(() -> variable("HOLDING_REGISTER", -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("non-negative");
    }

    @Test
    void encodingOnCoilIsRejected() {
        assertThatThrownBy(() -> variable("COIL", 0, "BIG_ENDIAN"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("encoding requires");
    }

    @Test
    void nonVariableNodeCannotCarryABinding() {
        assertThatThrownBy(() -> new SchemaNode(
                        "f1", null, "Plant", "Plant", NodeKind.FOLDER,
                        null, null, null, null, null, List.of(), null, List.of(), null, List.of(),
                        List.of(), null, null, null, null, null, null, null, "HOLDING_REGISTER", 1000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("modbusRegisterKind");
    }

    private static SchemaNode variable(String registerKind, Integer address) {
        return variable(registerKind, address, null);
    }

    private static SchemaNode variable(String registerKind, Integer address, String byteOrder) {
        return new SchemaNode(
                "n1", null, "Plant/Temp", "Temp", NodeKind.VARIABLE,
                DataType.UINT16, ValueRank.SCALAR, Access.READ_WRITE, null, null,
                List.of(), null, List.of(), null, List.of(), List.of(), null, null,
                null, null, null, null, null, registerKind, address, byteOrder, null, null);
    }
}
