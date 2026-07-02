package com.ainclusive.iotsim.domain.datasource;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SimulatorUrlTest {

    @Test
    void opcUaUrlHasSchemeHostPortAndPath() {
        assertThat(SimulatorUrl.of(Protocol.OPC_UA, "plant.local", 4840))
                .isEqualTo("opc.tcp://plant.local:4840/iotsim");
    }

    @Test
    void modbusUrlHasSchemeHostPort() {
        assertThat(SimulatorUrl.of(Protocol.MODBUS_TCP, "plant.local", 502))
                .isEqualTo("modbus.tcp://plant.local:502");
    }

    @Test
    void defaultPortIsPerProtocol() {
        assertThat(SimulatorUrl.defaultPort(Protocol.OPC_UA)).isEqualTo(4840);
        assertThat(SimulatorUrl.defaultPort(Protocol.MODBUS_TCP)).isEqualTo(502);
    }
}
