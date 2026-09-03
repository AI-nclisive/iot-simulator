package com.ainclusive.iotsim.worker.modbus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import com.ainclusive.iotsim.workercontract.v1.ModbusConnectionConfigMsg;
import com.ainclusive.iotsim.workercontract.v1.ModbusSerialConfigMsg;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModbusSerialSettingsTest {

    @Test
    void tcpIsTheBackwardCompatibleDefault() {
        assertThat(ModbusSerialSettings.fromOptions(Map.of())).isNull();
    }

    @Test
    void rtuSettingsProduceAnRtuJ2modConnection() {
        ModbusSerialSettings settings = ModbusSerialSettings.fromOptions(Map.of(
                "transport", "RTU", "serialPort", "/dev/ttyUSB0", "serialBaudRate", "19200",
                "serialDataBits", "8", "serialParity", "EVEN", "serialStopBits", "1"));

        assertThat(settings.port()).isEqualTo("/dev/ttyUSB0");
        assertThat(settings.baudRate()).isEqualTo(19200);
        assertThat(settings.toJ2mod().getEncoding()).isEqualTo("rtu");
        assertThat(settings.toJ2mod().getParityString()).isEqualTo("even");
    }

    @Test
    void structuredWorkerRequestConfigUsesTheSameValidationAndDefaults() {
        ModbusSerialSettings settings = ModbusSerialSettings.fromProto(ModbusConnectionConfigMsg.newBuilder()
                .setTransport(ModbusConnectionConfigMsg.Transport.RTU)
                .setSerial(ModbusSerialConfigMsg.newBuilder().setPort("COM7").setBaudRate(38400).build())
                .build());

        assertThat(settings).isEqualTo(new ModbusSerialSettings("COM7", 38400, 8, "NONE", "1"));
    }

    @Test
    void invalidSerialSettingsAreRejectedBeforeOpeningAPort() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ModbusSerialSettings.fromOptions(Map.of("transport", "RTU")))
                .withMessage("serialPort is required for Modbus RTU");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ModbusSerialSettings.fromOptions(Map.of(
                        "transport", "RTU", "serialPort", "COM1", "serialParity", "MARK")))
                .withMessage("serialParity must be NONE, EVEN, or ODD");
    }
}
