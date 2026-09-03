package com.ainclusive.iotsim.worker.modbus;

import com.ainclusive.iotsim.workercontract.v1.ModbusConnectionConfigMsg;
import com.ghgande.j2mod.modbus.Modbus;
import com.ghgande.j2mod.modbus.util.SerialParameters;
import java.util.Locale;
import java.util.Map;

/**
 * Validated Modbus RTU connection settings supplied to the worker as Configure
 * options. TCP remains the default when {@code transport} is absent or {@code TCP}.
 */
record ModbusSerialSettings(String port, int baudRate, int dataBits, String parity, String stopBits) {

    static final String TRANSPORT_OPTION = "transport";
    static final String RTU = "RTU";

    static ModbusSerialSettings fromOptions(Map<String, String> options) {
        String transport = options.getOrDefault(TRANSPORT_OPTION, "TCP").trim().toUpperCase(Locale.ROOT);
        if ("TCP".equals(transport)) {
            return null;
        }
        if (!RTU.equals(transport)) {
            throw new IllegalArgumentException("unsupported Modbus transport: " + transport);
        }
        String port = require(options, "serialPort");
        int baudRate = parsePositive(options.getOrDefault("serialBaudRate", "9600"), "serialBaudRate");
        int dataBits = parseInt(options.getOrDefault("serialDataBits", "8"), "serialDataBits");
        if (dataBits < 5 || dataBits > 8) {
            throw new IllegalArgumentException("serialDataBits must be between 5 and 8");
        }
        String parity = options.getOrDefault("serialParity", "NONE").trim().toUpperCase(Locale.ROOT);
        if (!parity.equals("NONE") && !parity.equals("EVEN") && !parity.equals("ODD")) {
            throw new IllegalArgumentException("serialParity must be NONE, EVEN, or ODD");
        }
        String stopBits = options.getOrDefault("serialStopBits", "1").trim();
        if (!stopBits.equals("1") && !stopBits.equals("1.5") && !stopBits.equals("2")) {
            throw new IllegalArgumentException("serialStopBits must be 1, 1.5, or 2");
        }
        return new ModbusSerialSettings(port, baudRate, dataBits, parity, stopBits);
    }

    static ModbusSerialSettings fromProto(ModbusConnectionConfigMsg config) {
        if (config == null || config.getTransport() == ModbusConnectionConfigMsg.Transport.TRANSPORT_UNSPECIFIED
                || config.getTransport() == ModbusConnectionConfigMsg.Transport.TCP) {
            return null;
        }
        if (config.getTransport() != ModbusConnectionConfigMsg.Transport.RTU) {
            throw new IllegalArgumentException("unsupported Modbus transport: " + config.getTransport());
        }
        var serial = config.getSerial();
        return fromOptions(Map.of(
                TRANSPORT_OPTION, RTU,
                "serialPort", serial.getPort(),
                "serialBaudRate", String.valueOf(serial.getBaudRate() == 0 ? 9600 : serial.getBaudRate()),
                "serialDataBits", String.valueOf(serial.getDataBits() == 0 ? 8 : serial.getDataBits()),
                "serialParity", serial.getParity().isBlank() ? "NONE" : serial.getParity(),
                "serialStopBits", serial.getStopBits().isBlank() ? "1" : serial.getStopBits()));
    }

    SerialParameters toJ2mod() {
        SerialParameters params = new SerialParameters();
        params.setPortName(port);
        params.setBaudRate(baudRate);
        params.setDatabits(dataBits);
        params.setParity(parity);
        params.setStopbits(stopBits);
        params.setEncoding(Modbus.SERIAL_ENCODING_RTU);
        return params;
    }

    private static String require(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(key + " is required for Modbus RTU");
        }
        return value.trim();
    }

    private static int parsePositive(String value, String name) {
        int parsed = parseInt(value, name);
        if (parsed <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return parsed;
    }

    private static int parseInt(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(name + " must be an integer", e);
        }
    }
}
