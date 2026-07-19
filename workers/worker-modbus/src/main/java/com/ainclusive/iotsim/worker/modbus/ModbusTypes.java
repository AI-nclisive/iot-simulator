package com.ainclusive.iotsim.worker.modbus;

/**
 * Maps the protocol-neutral data types onto Modbus's native register/coil model.
 * Mirrors {@code OpcUaTypes} in the worker-opcua module — see
 * backend-specs/01_PROTOCOL_NEUTRAL_MODEL.md §2 for the "superset, not
 * intersection" rule this follows.
 *
 * <p><strong>Scaffold placeholder</strong> — {@code worker-modbus} has no gRPC
 * {@code ProtocolDataSource} service or scan/discovery implementation yet
 * ({@link ModbusWorkerMain} is a TODO stub), so nothing calls this class today.
 * Only the single-register cases are filled in; multi-register combos
 * (INT32/UINT32/FLOAT32 spanning two registers) need a byte-order convention
 * (word order, endianness) that hasn't been decided yet — do not guess one here,
 * decide it when the real discovery/read path is implemented.
 */
final class ModbusTypes {

    private ModbusTypes() {}

    /**
     * Reverse mapping: a Modbus register/coil kind back to a protocol-neutral
     * data type. Coils/discrete inputs are single-bit → {@code BOOL}; a single
     * 16-bit holding/input register maps to {@code UINT16}/{@code INT16}
     * depending on whether the source declares it signed.
     *
     * <p>TODO: wire this into a real scan/discovery path once one exists, and
     * add multi-register (32/64-bit) mapping once the byte-order convention is
     * decided (see class javadoc).
     */
    static String neutralTypeOf(ModbusRegisterKind kind, boolean signed) {
        return switch (kind) {
            case COIL, DISCRETE_INPUT -> "BOOL";
            case HOLDING_REGISTER, INPUT_REGISTER -> signed ? "INT16" : "UINT16";
        };
    }

    /** Placeholder register/coil classification — replace with the real Modbus model. */
    enum ModbusRegisterKind {
        COIL,
        DISCRETE_INPUT,
        HOLDING_REGISTER,
        INPUT_REGISTER
    }

    // TODO: dataTypeId()/defaultValue()/to-from-native value conversion equivalents
    // to OpcUaTypes — add alongside the real gRPC service, once there's a caller.
}
