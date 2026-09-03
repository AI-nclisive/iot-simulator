package com.ainclusive.iotsim.worker.modbus;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.ValueCodec;
import org.junit.jupiter.api.Test;

class ModbusTypesTest {

    @Test
    void boolRoundTripsThroughCoilValue() {
        assertThat(ModbusTypes.toCoilValue(Boolean.TRUE)).isTrue();
        assertThat(ModbusTypes.toCoilValue(Boolean.FALSE)).isFalse();
    }

    @Test
    void uint16RoundTripsThroughASingleRegister() {
        int[] registers = ModbusTypes.toRegisters("UINT16", 4200L);
        assertThat(registers).containsExactly(4200);
        assertThat(ModbusTypes.fromRegisters("UINT16", registers)).isEqualTo(4200L);
    }

    @Test
    void int16NegativeRoundTrips() {
        int[] registers = ModbusTypes.toRegisters("INT16", -5L);
        assertThat(ModbusTypes.fromRegisters("INT16", registers)).isEqualTo(-5L);
    }

    @Test
    void int32RoundTripsBigEndianMswFirst() {
        long value = 70_000L;
        int[] registers = ModbusTypes.toRegisters("INT32", value);
        assertThat(registers).hasSize(2);
        // 70000 = 0x00011170 -> MSW=0x0001, LSW=0x1170
        assertThat(registers[0]).isEqualTo(0x0001);
        assertThat(registers[1]).isEqualTo(0x1170);
        assertThat(ModbusTypes.fromRegisters("INT32", registers)).isEqualTo(value);
    }

    @Test
    void uint32RoundTrips() {
        long value = 4_000_000_000L;
        int[] registers = ModbusTypes.toRegisters("UINT32", value);
        assertThat(ModbusTypes.fromRegisters("UINT32", registers)).isEqualTo(value);
    }

    @Test
    void float32RoundTrips() {
        double value = 123.5;
        int[] registers = ModbusTypes.toRegisters("FLOAT32", value);
        assertThat(registers).hasSize(2);
        assertThat((Double) ModbusTypes.fromRegisters("FLOAT32", registers)).isEqualTo(value);
    }

    @Test
    void configuredByteWordOrderAndScaleRoundTripFloat32() {
        int[] registers = ModbusTypes.toRegisters("FLOAT32", 12.5d, "LITTLE_ENDIAN", "LSW_FIRST", 0.1d);
        assertThat(registers).containsExactly(0x0000, 0xFA42);
        assertThat((Double) ModbusTypes.fromRegisters("FLOAT32", registers, "LITTLE_ENDIAN", "LSW_FIRST", 0.1d))
                .isEqualTo(12.5d);
    }

    @Test
    void configuredOrderAndScaleRoundTripSignedAndUnsignedIntegers() {
        int[] signed = ModbusTypes.toRegisters("INT16", -12L, "LITTLE_ENDIAN", null, 1d);
        assertThat(ModbusTypes.fromRegisters("INT16", signed, "LITTLE_ENDIAN", null, 1d)).isEqualTo(-12L);
        int[] unsigned = ModbusTypes.toRegisters("UINT32", 123_400L, "BIG_ENDIAN", "LSW_FIRST", 100d);
        assertThat(ModbusTypes.fromRegisters("UINT32", unsigned, "BIG_ENDIAN", "LSW_FIRST", 100d)).isEqualTo(123_400L);
    }

    @Test
    void registerSpanMatchesTypeWidth() {
        assertThat(ModbusTypes.registerSpan("UINT16")).isEqualTo(1);
        assertThat(ModbusTypes.registerSpan("INT16")).isEqualTo(1);
        assertThat(ModbusTypes.registerSpan("INT32")).isEqualTo(2);
        assertThat(ModbusTypes.registerSpan("UINT32")).isEqualTo(2);
        assertThat(ModbusTypes.registerSpan("FLOAT32")).isEqualTo(2);
        assertThat(ModbusTypes.registerSpan("BOOL")).isEqualTo(0);
    }

    @Test
    void codecKindMatchesValueCodec() {
        assertThat(ModbusTypes.codecKind("BOOL")).isEqualTo(ValueCodec.Kind.BOOL);
        assertThat(ModbusTypes.codecKind("UINT16")).isEqualTo(ValueCodec.Kind.INT);
        assertThat(ModbusTypes.codecKind("INT32")).isEqualTo(ValueCodec.Kind.INT);
        assertThat(ModbusTypes.codecKind("FLOAT32")).isEqualTo(ValueCodec.Kind.NUM);
    }

    @Test
    void isSupportedRejectsUnknownTypes() {
        assertThat(ModbusTypes.isSupported("BOOL")).isTrue();
        assertThat(ModbusTypes.isSupported("FLOAT64")).isFalse();
        assertThat(ModbusTypes.isSupported("STRING")).isFalse();
    }
}
