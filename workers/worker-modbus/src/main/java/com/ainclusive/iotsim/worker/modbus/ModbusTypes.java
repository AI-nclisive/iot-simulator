package com.ainclusive.iotsim.worker.modbus;

import com.ainclusive.iotsim.protocolmodel.ValueCodec;

/**
 * Maps the protocol-neutral data types onto Modbus's native register/coil model.
 * Mirrors {@code OpcUaTypes} in the worker-opcua module — see
 * openspec/specs/protocol-model/spec.md §2 for the "superset, not
 * intersection" rule this follows.
 *
 * <p>Supported neutral types: {@code BOOL} (coil / discrete input), {@code
 * INT16}/{@code UINT16} (one register), {@code INT32}/{@code UINT32}/{@code
 * FLOAT32} (a register pair). Multi-register values use big-endian,
 * most-significant-register-first word order — see
 * openspec/changes/is-059-worker-modbus/design.md decision 2. {@code INT64}/
 * {@code UINT64}/{@code FLOAT64} (four-register spans) are intentionally not
 * supported yet: same convention would apply, but nothing exercises them,
 * so they are left as a documented follow-up rather than guessed here.
 */
final class ModbusTypes {

    private ModbusTypes() {}

    /**
     * Reverse mapping: a Modbus register/coil kind back to a protocol-neutral
     * data type, for the single-register default used during Scan.
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

    /** Neutral data types this worker can materialize over Modbus registers/coils. */
    static boolean isSupported(String dataType) {
        return switch (dataType) {
            case "BOOL", "INT16", "UINT16", "INT32", "UINT32", "FLOAT32" -> true;
            default -> false;
        };
    }

    /** Number of 16-bit registers a value of this type occupies (0 for coil/discrete-input types). */
    static int registerSpan(String dataType) {
        return switch (dataType) {
            case "BOOL" -> 0;
            case "INT16", "UINT16" -> 1;
            case "INT32", "UINT32", "FLOAT32" -> 2;
            default -> throw new IllegalArgumentException("unsupported Modbus data type: " + dataType);
        };
    }

    /** The {@link ValueCodec.Kind} used to decode/encode a value of this neutral type over the wire. */
    static ValueCodec.Kind codecKind(String dataType) {
        return switch (dataType) {
            case "BOOL" -> ValueCodec.Kind.BOOL;
            case "INT16", "UINT16", "INT32", "UINT32" -> ValueCodec.Kind.INT;
            case "FLOAT32" -> ValueCodec.Kind.NUM;
            default -> throw new IllegalArgumentException("unsupported Modbus data type: " + dataType);
        };
    }

    /** Default value used to seed a register/coil before any value has been applied. */
    static Object defaultValue(String dataType) {
        return switch (dataType) {
            case "BOOL" -> Boolean.FALSE;
            case "INT16", "UINT16", "INT32", "UINT32" -> 0L;
            case "FLOAT32" -> 0.0;
            default -> throw new IllegalArgumentException("unsupported Modbus data type: " + dataType);
        };
    }

    /**
     * Encodes a decoded neutral value (from {@link ValueCodec#decode}) into raw
     * 16-bit register words, big-endian / most-significant-register-first.
     * Length matches {@link #registerSpan(String)}.
     */
    static int[] toRegisters(String dataType, Object neutralValue) {
        return switch (dataType) {
            case "INT16", "UINT16" -> new int[] {((Number) neutralValue).intValue() & 0xFFFF};
            case "INT32", "UINT32" -> {
                int value = ((Number) neutralValue).intValue();
                yield new int[] {(value >>> 16) & 0xFFFF, value & 0xFFFF};
            }
            case "FLOAT32" -> {
                int bits = Float.floatToRawIntBits(((Number) neutralValue).floatValue());
                yield new int[] {(bits >>> 16) & 0xFFFF, bits & 0xFFFF};
            }
            default -> throw new IllegalArgumentException("unsupported Modbus data type: " + dataType);
        };
    }

    /**
     * Decodes raw 16-bit register words (big-endian / MSW-first) back into a
     * neutral value, ready for {@link ValueCodec#encode}. Used by Scan/Capture
     * (reading from a real device) rather than the simulated server path.
     */
    static Object fromRegisters(String dataType, int[] registers) {
        return switch (dataType) {
            case "INT16" -> (long) (short) registers[0];
            case "UINT16" -> (long) (registers[0] & 0xFFFF);
            case "INT32" -> (long) ((registers[0] << 16) | (registers[1] & 0xFFFF));
            case "UINT32" -> (((long) registers[0] & 0xFFFF) << 16) | (registers[1] & 0xFFFF);
            case "FLOAT32" -> (double) Float.intBitsToFloat((registers[0] << 16) | (registers[1] & 0xFFFF));
            default -> throw new IllegalArgumentException("unsupported Modbus data type: " + dataType);
        };
    }

    /** Coerces a decoded neutral value into the boolean a coil/discrete-input stores. */
    static boolean toCoilValue(Object neutralValue) {
        return (Boolean) neutralValue;
    }
}
