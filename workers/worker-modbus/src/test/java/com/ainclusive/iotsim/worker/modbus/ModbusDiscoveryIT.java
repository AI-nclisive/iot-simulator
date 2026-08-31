package com.ainclusive.iotsim.worker.modbus;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.workercontract.v1.SchemaNodeMsg;
import java.net.InetAddress;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Scans a real local slave (built via {@link ModbusServerRuntime}, the same
 * runtime {@code Configure}/{@code ApplyValues} use) and verifies the active
 * probe finds exactly the configured addresses and stays bounded when a
 * whole object type is empty — see
 * openspec/changes/is-059-worker-modbus/design.md decision 3.
 */
class ModbusDiscoveryIT {

    private static final int PORT = 48621;

    private ModbusServerRuntime runtime;

    @AfterEach
    void tearDown() {
        if (runtime != null) {
            runtime.stop();
        }
    }

    @Test
    void discoversConfiguredHoldingRegistersAndStaysBoundedOnEmptyObjectTypes() throws Exception {
        List<ModbusServerRuntime.VarSpec> vars = List.of(
                new ModbusServerRuntime.VarSpec("hr1", "UINT16", "READ_WRITE"),
                new ModbusServerRuntime.VarSpec("hr2", "UINT16", "READ_WRITE"),
                new ModbusServerRuntime.VarSpec("hr3", "UINT16", "READ_WRITE"));
        runtime = new ModbusServerRuntime(vars, PORT, InetAddress.getByName("127.0.0.1"), 1, event -> {});
        runtime.start();

        ModbusDiscovery.ScanOutcome outcome = ModbusDiscovery.scan(
                "127.0.0.1:" + PORT, 0, () -> {}, soFar -> {});

        assertThat(outcome.status()).isIn("OK", "PARTIAL");
        Set<String> nodeIds = outcome.nodes().stream().map(SchemaNodeMsg::getNodeId).collect(Collectors.toSet());
        assertThat(nodeIds).contains("hr:0", "hr:1", "hr:2");
        // Every discovered node in the holding-register series is an adjacent pair
        // of the three configured registers, so the 32-bit pairing heuristic
        // should have flagged at least one advisory node.
        assertThat(nodeIds.stream().anyMatch(id -> id.startsWith("hr32:"))).isTrue();
        // Coils/discrete-inputs/input-registers are entirely unconfigured (empty
        // process image): the bounded probe must not have found anything there.
        assertThat(nodeIds.stream().noneMatch(id -> id.startsWith("co:") || id.startsWith("di:") || id.startsWith("ir:")))
                .isTrue();
    }

    @Test
    void unreachableEndpointReturnsUnreachableWithoutHanging() {
        ModbusDiscovery.ScanOutcome outcome = ModbusDiscovery.scan(
                "127.0.0.1:1", 0, () -> {}, soFar -> {});
        assertThat(outcome.status()).isEqualTo("UNREACHABLE");
        assertThat(outcome.nodes()).isEmpty();
    }
}
