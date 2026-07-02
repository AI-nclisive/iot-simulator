package com.ainclusive.iotsim.worker.opcua;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class OpcUaServerRuntimeTest {

    @Test
    void endpointUrlUsesAdvertisedHost() {
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(
                4840, "0.0.0.0", "plant.local", List.of(), event -> {}, event -> {});
        assertThat(runtime.endpointUrl()).isEqualTo("opc.tcp://plant.local:4840/iotsim");
    }

    @Test
    void loopbackConstructorDefaultsAdvertisedHostToLoopback() {
        OpcUaServerRuntime runtime = new OpcUaServerRuntime(4840, List.of());
        assertThat(runtime.endpointUrl()).isEqualTo("opc.tcp://127.0.0.1:4840/iotsim");
    }
}
