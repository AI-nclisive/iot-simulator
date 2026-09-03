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

    /** The four Modbus object types every register/coil address belongs to. */
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
        return toRegisters(dataType, neutralValue, null, null, null);
    }

    /** Encodes an engineering value using optional vendor-specific register layout metadata. */
    static int[] toRegisters(String dataType, Object neutralValue, String byteOrder, String wordOrder, Double scale) {
        double factor = scale == null ? 1.0d : scale;
        Object rawValue = switch (dataType) {
            case "INT16", "UINT16", "INT32", "UINT32" -> Math.round(((Number) neutralValue).doubleValue() / factor);
            case "FLOAT32" -> ((Number) neutralValue).doubleValue() / factor;
            default -> neutralValue;
        };
        int[] registers = switch (dataType) {
            case "INT16", "UINT16" -> new int[] {((Number) rawValue).intValue() & 0xFFFF};
            case "INT32", "UINT32" -> splitWords(((Number) rawValue).intValue());
            case "FLOAT32" -> splitWords(Float.floatToRawIntBits(((Number) rawValue).floatValue()));
            default -> throw new IllegalArgumentException("unsupported Modbus data type: " + dataType);
        };
        return applyOrder(registers, byteOrder, wordOrder);
    }

    /** Splits a 32-bit value into {MSW, LSW} per the MSW-first convention (design.md decision 2). */
    private static int[] splitWords(int value) {
        return new int[] {(value >>> 16) & 0xFFFF, value & 0xFFFF};
    }

    /** Recombines {MSW, LSW} registers into a 32-bit value per the MSW-first convention. */
    private static int combineWords(int[] registers) {
        return (registers[0] << 16) | (registers[1] & 0xFFFF);
    }

    /**
     * Decodes raw 16-bit register words (big-endian / MSW-first) back into a
     * neutral value, ready for {@link ValueCodec#encode}. Used by Scan/Capture
     * (reading from a real device) rather than the simulated server path.
     */
    static Object fromRegisters(String dataType, int[] registers) {
        return fromRegisters(dataType, registers, null, null, null);
    }

    /** Decodes vendor-ordered raw registers into a neutral engineering value. */
    static Object fromRegisters(String dataType, int[] registers, String byteOrder, String wordOrder, Double scale) {
        int[] normalized = undoOrder(registers, byteOrder, wordOrder);
        Object raw = switch (dataType) {
            case "INT16" -> (long) (short) normalized[0];
            case "UINT16" -> (long) (normalized[0] & 0xFFFF);
            case "INT32" -> (long) combineWords(normalized);
            case "UINT32" -> combineWords(normalized) & 0xFFFFFFFFL;
            case "FLOAT32" -> (double) Float.intBitsToFloat(combineWords(normalized));
            default -> throw new IllegalArgumentException("unsupported Modbus data type: " + dataType);
        };
        if (scale == null) {
            return raw;
        }
        double engineering = ((Number) raw).doubleValue() * scale;
        if ("FLOAT32".equals(dataType)) {
            return engineering;
        }
        return Math.round(engineering);
    }

    private static int[] applyOrder(int[] registers, String byteOrder, String wordOrder) {
        int[] result = registers.clone();
        if ("LITTLE_ENDIAN".equals(byteOrder)) {
            for (int i = 0; i < result.length; i++) {
                result[i] = swapBytes(result[i]);
            }
        }
        if (result.length > 1 && "LSW_FIRST".equals(wordOrder)) {
            for (int i = 0; i < result.length / 2; i++) {
                int j = result.length - 1 - i;
                int value = result[i]; result[i] = result[j]; result[j] = value;
            }
        }
        return result;
    }

    private static int[] undoOrder(int[] registers, String byteOrder, String wordOrder) {
        // Both supported transforms are involutions, so the inverse is the same operation.
        return applyOrder(registers, byteOrder, wordOrder);
    }

    private static int swapBytes(int word) {
        return ((word & 0xFF) << 8) | ((word >>> 8) & 0xFF);
    }

    /** Coerces a decoded neutral value into the boolean a coil/discrete-input stores. */
    static boolean toCoilValue(Object neutralValue) {
        return (Boolean) neutralValue;
    }
}
