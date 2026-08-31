package com.ainclusive.iotsim.worker.modbus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Pure unit tests for the endpoint/nodeId address-encoding convention, no network. */
class ModbusDiscoveryTest {

    @Test
    void parseEndpointDefaultsUnitIdWhenAbsent() {
        ModbusDiscovery.Endpoint endpoint = ModbusDiscovery.parseEndpoint("10.20.4.40:502");
        assertThat(endpoint.host()).isEqualTo("10.20.4.40");
        assertThat(endpoint.port()).isEqualTo(502);
        assertThat(endpoint.unitId()).isEqualTo(ModbusDiscovery.DEFAULT_UNIT_ID);
    }

    @Test
    void parseEndpointReadsUnitIdSuffix() {
        ModbusDiscovery.Endpoint endpoint = ModbusDiscovery.parseEndpoint("10.20.4.40:502#7");
        assertThat(endpoint.unitId()).isEqualTo(7);
    }

    @Test
    void parseNodeAddressResolvesPlainPrefixes() {
        assertThat(ModbusDiscovery.parseNodeAddress("co:5").kind()).isEqualTo(ModbusTypes.ModbusRegisterKind.COIL);
        assertThat(ModbusDiscovery.parseNodeAddress("di:5").kind())
                .isEqualTo(ModbusTypes.ModbusRegisterKind.DISCRETE_INPUT);
        assertThat(ModbusDiscovery.parseNodeAddress("hr:1000").kind())
                .isEqualTo(ModbusTypes.ModbusRegisterKind.HOLDING_REGISTER);
        assertThat(ModbusDiscovery.parseNodeAddress("ir:1000").kind())
                .isEqualTo(ModbusTypes.ModbusRegisterKind.INPUT_REGISTER);
        assertThat(ModbusDiscovery.parseNodeAddress("hr:1000").address()).isEqualTo(1000);
    }

    @Test
    void parseNodeAddressResolvesAdvisoryPairPrefixToTheSameBaseAddress() {
        // A user-accepted "hr32:1000"/"ir32:1000" advisory node (see ModbusDiscovery's pair
        // heuristic) must resolve to the same object type/address as the plain "hr:1000" form
        // it was derived from — otherwise Capture silently drops every resolved pair node.
        ModbusDiscovery.NodeAddress fromPair = ModbusDiscovery.parseNodeAddress("hr32:1000");
        ModbusDiscovery.NodeAddress fromPlain = ModbusDiscovery.parseNodeAddress("hr:1000");
        assertThat(fromPair).isEqualTo(fromPlain);
        assertThat(ModbusDiscovery.parseNodeAddress("ir32:1000").kind())
                .isEqualTo(ModbusTypes.ModbusRegisterKind.INPUT_REGISTER);
    }

    @Test
    void parseNodeAddressRejectsUnknownPrefix() {
        assertThatThrownBy(() -> ModbusDiscovery.parseNodeAddress("xx:1"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
