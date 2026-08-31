package com.ainclusive.iotsim.worker.modbus;

import static org.assertj.core.api.Assertions.assertThat;

import com.ghgande.j2mod.modbus.facade.ModbusTCPMaster;
import com.ghgande.j2mod.modbus.procimg.InputRegister;
import java.net.InetAddress;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Configures a real j2mod slave via {@link ModbusServerRuntime} and reads/writes
 * it with a real j2mod master, verifying the default contiguous layout and the
 * register/coil write path used by {@code ApplyValues}.
 */
class ModbusServerRuntimeIT {

    private static final int PORT = 48611;

    private ModbusServerRuntime runtime;
    private ModbusTCPMaster master;

    @AfterEach
    void tearDown() {
        if (master != null) {
            master.disconnect();
        }
        if (runtime != null) {
            runtime.stop();
        }
    }

    @Test
    void writesAndReadsCoilsAndRegistersAtTheDefaultLayout() throws Exception {
        List<ModbusServerRuntime.VarSpec> vars = List.of(
                new ModbusServerRuntime.VarSpec("coil1", "BOOL", "READ_WRITE"),
                new ModbusServerRuntime.VarSpec("di1", "BOOL", "READ"),
                new ModbusServerRuntime.VarSpec("hr1", "UINT16", "READ_WRITE"),
                new ModbusServerRuntime.VarSpec("hr2", "INT32", "READ_WRITE"),
                new ModbusServerRuntime.VarSpec("ir1", "FLOAT32", "READ"));
        runtime = new ModbusServerRuntime(vars, PORT, InetAddress.getByName("127.0.0.1"), 1, event -> {});
        runtime.start();

        master = new ModbusTCPMaster("127.0.0.1", PORT, 2000, false);
        master.connect();

        // Default layout: coil1 -> coil address 0; hr1 -> holding register 0 (span 1);
        // hr2 -> holding registers 1-2 (span 2, INT32).
        assertThat(master.readCoils(1, 0, 1).getBit(0)).isFalse();
        runtime.updateValue("coil1", true);
        assertThat(master.readCoils(1, 0, 1).getBit(0)).isTrue();

        assertThat(master.readInputDiscretes(1, 0, 1).getBit(0)).isFalse();
        runtime.updateValue("di1", true);
        assertThat(master.readInputDiscretes(1, 0, 1).getBit(0)).isTrue();

        runtime.updateValue("hr1", 4200L);
        assertThat(unsigned(master.readMultipleRegisters(1, 0, 1))[0]).isEqualTo(4200);

        runtime.updateValue("hr2", 70_000L);
        int[] hr2 = unsigned(master.readMultipleRegisters(1, 1, 2));
        assertThat(ModbusTypes.fromRegisters("INT32", hr2)).isEqualTo(70_000L);

        runtime.updateValue("ir1", 3.5);
        int[] ir1 = unsigned(master.readInputRegisters(1, 0, 2));
        assertThat((double) ModbusTypes.fromRegisters("FLOAT32", ir1)).isEqualTo(3.5);
    }

    @Test
    void assignmentsReflectTheDefaultContiguousLayout() throws Exception {
        List<ModbusServerRuntime.VarSpec> vars = List.of(
                new ModbusServerRuntime.VarSpec("hr1", "UINT16", "READ_WRITE"),
                new ModbusServerRuntime.VarSpec("hr2", "INT32", "READ_WRITE"),
                new ModbusServerRuntime.VarSpec("hr3", "UINT16", "READ_WRITE"));
        runtime = new ModbusServerRuntime(vars, PORT + 1, InetAddress.getByName("127.0.0.1"), 1, event -> {});

        assertThat(runtime.assignments().get("hr1").address()).isEqualTo(0);
        assertThat(runtime.assignments().get("hr2").address()).isEqualTo(1);
        assertThat(runtime.assignments().get("hr3").address()).isEqualTo(3);
    }

    private static int[] unsigned(InputRegister[] registers) {
        int[] raw = new int[registers.length];
        for (int i = 0; i < registers.length; i++) {
            raw[i] = registers[i].toUnsignedShort();
        }
        return raw;
    }
}
