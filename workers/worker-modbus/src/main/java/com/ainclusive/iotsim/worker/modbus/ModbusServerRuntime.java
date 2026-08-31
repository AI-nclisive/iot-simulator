package com.ainclusive.iotsim.worker.modbus;

import com.ainclusive.iotsim.worker.modbus.ModbusTypes.ModbusRegisterKind;
import com.ainclusive.iotsim.workercontract.v1.RuntimeEvent;
import com.ghgande.j2mod.modbus.ModbusException;
import com.ghgande.j2mod.modbus.procimg.SimpleDigitalIn;
import com.ghgande.j2mod.modbus.procimg.SimpleDigitalOut;
import com.ghgande.j2mod.modbus.procimg.SimpleInputRegister;
import com.ghgande.j2mod.modbus.procimg.SimpleProcessImage;
import com.ghgande.j2mod.modbus.procimg.SimpleRegister;
import com.ghgande.j2mod.modbus.slave.ModbusSlave;
import com.ghgande.j2mod.modbus.slave.ModbusSlaveFactory;
import java.net.InetAddress;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * The simulated Modbus TCP slave: a j2mod {@link SimpleProcessImage} built from
 * the neutral schema, using the protocol-model default contiguous register
 * layout (schema order -> address, per object type) — see
 * openspec/specs/protocol-model/spec.md §5 and
 * openspec/changes/is-059-worker-modbus/design.md. Mirrors
 * {@code worker-opcua}'s {@code OpcUaServerRuntime}.
 *
 * <p>Register/coil object type is chosen from each variable's declared
 * {@code access}: {@code READ} projects to a read-only object type (discrete
 * input / input register), anything else (including unset) projects to a
 * writable one (coil / holding register) — the same "default rule, worker
 * decides deterministically" pattern OPC UA folders/variables use.
 */
final class ModbusServerRuntime {

    /** One assigned node: its Modbus object type, base address, and neutral data type. */
    record NodeAssignment(String dataType, ModbusRegisterKind kind, int address) {}

    private final SimpleProcessImage image = new SimpleProcessImage();
    private final Map<String, NodeAssignment> assignments;
    private final int unitId;
    private final int listenPort;
    private final InetAddress bindAddress;
    private final Consumer<RuntimeEvent> runtimeEventSink;
    private ModbusSlave slave;

    /** One schema variable as seen by the runtime: its node id, neutral data type, and declared access. */
    record VarSpec(String nodeId, String dataType, String access) {}

    ModbusServerRuntime(List<VarSpec> vars, int listenPort, InetAddress bindAddress, int unitId,
            Consumer<RuntimeEvent> runtimeEventSink) {
        this.listenPort = listenPort;
        this.bindAddress = bindAddress;
        this.unitId = unitId;
        this.runtimeEventSink = runtimeEventSink;
        this.assignments = layout(vars, image);
    }

    /** Node ids whose declared data type this worker cannot materialize over Modbus. */
    static Set<String> unsupportedNodes(List<VarSpec> vars) {
        return vars.stream()
                .filter(v -> !ModbusTypes.isSupported(v.dataType()))
                .map(VarSpec::nodeId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Map<String, NodeAssignment> layout(List<VarSpec> vars, SimpleProcessImage image) {
        Map<String, NodeAssignment> result = new LinkedHashMap<>();
        int coilAddr = 0;
        int discreteAddr = 0;
        int holdingAddr = 0;
        int inputAddr = 0;
        for (VarSpec var : vars) {
            if (!ModbusTypes.isSupported(var.dataType())) {
                continue;
            }
            boolean readOnly = "READ".equals(var.access());
            if ("BOOL".equals(var.dataType())) {
                int address = readOnly ? discreteAddr++ : coilAddr++;
                ModbusRegisterKind kind = readOnly ? ModbusRegisterKind.DISCRETE_INPUT : ModbusRegisterKind.COIL;
                result.put(var.nodeId(), new NodeAssignment(var.dataType(), kind, address));
                if (readOnly) {
                    image.addDigitalIn(address, new SimpleDigitalIn(false));
                } else {
                    image.addDigitalOut(address, new SimpleDigitalOut(false));
                }
                continue;
            }
            int span = ModbusTypes.registerSpan(var.dataType());
            int address = readOnly ? inputAddr : holdingAddr;
            if (readOnly) {
                inputAddr += span;
            } else {
                holdingAddr += span;
            }
            ModbusRegisterKind kind = readOnly ? ModbusRegisterKind.INPUT_REGISTER : ModbusRegisterKind.HOLDING_REGISTER;
            result.put(var.nodeId(), new NodeAssignment(var.dataType(), kind, address));
            int[] initial = ModbusTypes.toRegisters(var.dataType(), ModbusTypes.defaultValue(var.dataType()));
            for (int i = 0; i < initial.length; i++) {
                if (readOnly) {
                    image.addInputRegister(address + i, new SimpleInputRegister(initial[i]));
                } else {
                    image.addRegister(address + i, new SimpleRegister(initial[i]));
                }
            }
        }
        return result;
    }

    /** The address assignment computed for every representable variable (introspection/tests). */
    Map<String, NodeAssignment> assignments() {
        return assignments;
    }

    /**
     * Opens the TCP listener. Note: unlike Milo, j2mod's listener treats
     * {@code listenPort == 0} as "use the Modbus default port (502)", not
     * "pick an ephemeral port" — the supervisor always assigns a real port in
     * production, so this only matters for tests, which must use an explicit
     * port.
     */
    void start() throws ModbusStartException {
        if (slave != null) {
            // A repeated Start (e.g. a retried RPC) must not leak the previous
            // listener/socket — close it before opening a new one.
            ModbusSlaveFactory.close(slave);
            slave = null;
        }
        try {
            ModbusSlave opened = ModbusSlaveFactory.createTCPSlave(bindAddress, listenPort, 2, false);
            opened.addProcessImage(unitId, image);
            opened.open();
            // Only assign on success: if open() throws, nothing is left half-bound on this.slave.
            slave = opened;
        } catch (ModbusException e) {
            throw new ModbusStartException(e.getMessage());
        }
    }

    void stop() {
        if (slave != null) {
            ModbusSlaveFactory.close(slave);
            slave = null;
        }
    }

    /** Writes a decoded neutral value into the register/coil backing the given node. */
    void updateValue(String nodeId, Object decoded) {
        NodeAssignment assignment = assignments.get(nodeId);
        if (assignment == null) {
            return;
        }
        switch (assignment.kind()) {
            case COIL -> image.addDigitalOut(assignment.address(), new SimpleDigitalOut(ModbusTypes.toCoilValue(decoded)));
            case DISCRETE_INPUT -> image.addDigitalIn(assignment.address(), new SimpleDigitalIn(ModbusTypes.toCoilValue(decoded)));
            case HOLDING_REGISTER -> {
                int[] regs = ModbusTypes.toRegisters(assignment.dataType(), decoded);
                for (int i = 0; i < regs.length; i++) {
                    image.addRegister(assignment.address() + i, new SimpleRegister(regs[i]));
                }
            }
            case INPUT_REGISTER -> {
                int[] regs = ModbusTypes.toRegisters(assignment.dataType(), decoded);
                for (int i = 0; i < regs.length; i++) {
                    image.addInputRegister(assignment.address() + i, new SimpleInputRegister(regs[i]));
                }
            }
        }
    }

    void emitRuntimeEvent(String type, String detail) {
        runtimeEventSink.accept(RuntimeEvent.newBuilder()
                .setType(type)
                .setAtMicros(System.currentTimeMillis() * 1_000L)
                .setDetail(detail)
                .build());
    }

    /** Thrown when the TCP listener cannot bind (e.g. the port is already in use). */
    static final class ModbusStartException extends Exception {
        ModbusStartException(String message) {
            super(message);
        }
    }
}
