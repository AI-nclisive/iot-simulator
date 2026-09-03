package com.ainclusive.iotsim.domain.manualschema;

import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Reusable Modbus device profiles for manual schema creation. */
public final class ModbusTemplates {
    private static final String HOLDING_REGISTER = "HOLDING_REGISTER";

    private ModbusTemplates() {}

    /**
     * SunSpec three-phase inverter profile using Common Model 1 and Inverter
     * Model 103. Addresses are zero-based holding-register offsets: the
     * published SunSpec address 40000 is offset 0.
     *
     * <p>The profile deliberately includes only fields materializable by the
     * current Modbus worker. Fixed-width SunSpec text fields are excluded until
     * the worker supports string values.
     */
    public static List<SchemaNode> sunspecInverter() {
        return List.of(
                folder("sunspec_inverter", null, "SunSpec Inverter", "SunSpec Inverter"),
                register("sunspec_header", "sunspec_inverter", "SunSpec Inverter/Header", "Header", DataType.UINT32,
                        0, "SunSpec 'SunS' beginning-of-models marker, most-significant register first"),
                folder("sunspec_common_model", "sunspec_inverter", "SunSpec Inverter/Common Model 1", "Common Model 1"),
                register("sunspec_common_model_id", "sunspec_common_model", "SunSpec Inverter/Common Model 1/Model ID",
                        "Model ID", DataType.UINT16, 2, "SunSpec Common Model identifier (1)"),
                register("sunspec_common_model_length", "sunspec_common_model", "SunSpec Inverter/Common Model 1/Length",
                        "Length", DataType.UINT16, 3, "SunSpec Common Model length"),
                register("sunspec_common_device_address", "sunspec_common_model", "SunSpec Inverter/Common Model 1/Device Address",
                        "Device Address", DataType.UINT16, 69, "SunSpec device address"),
                folder("sunspec_inverter_model", "sunspec_inverter", "SunSpec Inverter/Three-Phase Inverter Model 103",
                        "Three-Phase Inverter Model 103"),
                register("sunspec_inverter_model_id", "sunspec_inverter_model", "SunSpec Inverter/Three-Phase Inverter Model 103/Model ID",
                        "Model ID", DataType.UINT16, 70, "SunSpec three-phase inverter model identifier (103)"),
                register("sunspec_inverter_model_length", "sunspec_inverter_model", "SunSpec Inverter/Three-Phase Inverter Model 103/Length",
                        "Length", DataType.UINT16, 71, "SunSpec three-phase inverter model length"),
                register("sunspec_inverter_current", "sunspec_inverter_model", "SunSpec Inverter/Three-Phase Inverter Model 103/Current",
                        "Current", DataType.INT16, 72, "AC current, scaled by Current Scale Factor"),
                register("sunspec_inverter_current_phase_a", "sunspec_inverter_model", "SunSpec Inverter/Three-Phase Inverter Model 103/Current Phase A",
                        "Current Phase A", DataType.INT16, 73, "Phase A AC current"),
                register("sunspec_inverter_current_phase_b", "sunspec_inverter_model", "SunSpec Inverter/Three-Phase Inverter Model 103/Current Phase B",
                        "Current Phase B", DataType.INT16, 74, "Phase B AC current"),
                register("sunspec_inverter_current_phase_c", "sunspec_inverter_model", "SunSpec Inverter/Three-Phase Inverter Model 103/Current Phase C",
                        "Current Phase C", DataType.INT16, 75, "Phase C AC current"),
                register("sunspec_inverter_current_sf", "sunspec_inverter_model", "SunSpec Inverter/Three-Phase Inverter Model 103/Current Scale Factor",
                        "Current Scale Factor", DataType.INT16, 76, "SunSpec signed scale factor for AC current"),
                register("sunspec_inverter_power", "sunspec_inverter_model", "SunSpec Inverter/Three-Phase Inverter Model 103/AC Power",
                        "AC Power", DataType.INT16, 85, "AC power, scaled by AC Power Scale Factor"),
                register("sunspec_inverter_power_sf", "sunspec_inverter_model", "SunSpec Inverter/Three-Phase Inverter Model 103/AC Power Scale Factor",
                        "AC Power Scale Factor", DataType.INT16, 86, "SunSpec signed scale factor for AC power"),
                register("sunspec_inverter_frequency", "sunspec_inverter_model", "SunSpec Inverter/Three-Phase Inverter Model 103/Frequency",
                        "Frequency", DataType.INT16, 87, "AC frequency, scaled by Frequency Scale Factor"),
                register("sunspec_inverter_frequency_sf", "sunspec_inverter_model", "SunSpec Inverter/Three-Phase Inverter Model 103/Frequency Scale Factor",
                        "Frequency Scale Factor", DataType.INT16, 88, "SunSpec signed scale factor for AC frequency"),
                register("sunspec_inverter_watt_hours", "sunspec_inverter_model", "SunSpec Inverter/Three-Phase Inverter Model 103/AC Energy",
                        "AC Energy", DataType.UINT32, 95, "AC energy accumulator, most-significant register first"),
                register("sunspec_inverter_watt_hours_sf", "sunspec_inverter_model", "SunSpec Inverter/Three-Phase Inverter Model 103/AC Energy Scale Factor",
                        "AC Energy Scale Factor", DataType.INT16, 97, "SunSpec signed scale factor for AC energy"),
                register("sunspec_inverter_status", "sunspec_inverter_model", "SunSpec Inverter/Three-Phase Inverter Model 103/Status",
                        "Status", DataType.UINT16, 109, "SunSpec operating status"),
                register("sunspec_inverter_status_vendor", "sunspec_inverter_model", "SunSpec Inverter/Three-Phase Inverter Model 103/Vendor Status",
                        "Vendor Status", DataType.UINT16, 110, "Vendor-defined operating status"),
                register("sunspec_inverter_events", "sunspec_inverter_model", "SunSpec Inverter/Three-Phase Inverter Model 103/Events",
                        "Events", DataType.UINT32, 111, "SunSpec event bitfield, most-significant register first"));
    }

    /** Generic three-phase energy meter with common instantaneous measurements. */
    public static List<SchemaNode> genericEnergyMeter() {
        return List.of(
                folder("energy_meter", null, "Energy Meter", "Energy Meter"),
                register("energy_meter_voltage_l1", "energy_meter", "Energy Meter/Voltage L1", "Voltage L1", DataType.FLOAT32, 0,
                        "Line-to-neutral voltage, IEEE-754", HOLDING_REGISTER, Access.READ),
                register("energy_meter_voltage_l2", "energy_meter", "Energy Meter/Voltage L2", "Voltage L2", DataType.FLOAT32, 2,
                        "Line-to-neutral voltage, IEEE-754", HOLDING_REGISTER, Access.READ),
                register("energy_meter_voltage_l3", "energy_meter", "Energy Meter/Voltage L3", "Voltage L3", DataType.FLOAT32, 4,
                        "Line-to-neutral voltage, IEEE-754", HOLDING_REGISTER, Access.READ),
                register("energy_meter_current", "energy_meter", "Energy Meter/Current", "Current", DataType.FLOAT32, 6,
                        "Aggregate current, IEEE-754", HOLDING_REGISTER, Access.READ),
                register("energy_meter_active_power", "energy_meter", "Energy Meter/Active Power", "Active Power", DataType.FLOAT32, 8,
                        "Total active power, IEEE-754", HOLDING_REGISTER, Access.READ),
                register("energy_meter_energy", "energy_meter", "Energy Meter/Energy", "Energy", DataType.FLOAT32, 10,
                        "Total imported energy, IEEE-754", HOLDING_REGISTER, Access.READ));
    }

    /** Generic PLC digital I/O and two writable holding-register setpoints. */
    public static List<SchemaNode> genericPlcIo() {
        return List.of(
                folder("plc_io", null, "PLC I/O", "PLC I/O"),
                register("plc_coil_start", "plc_io", "PLC I/O/Start Command", "Start Command", DataType.BOOL, 0,
                        "Writable start command", "COIL", Access.READ_WRITE),
                register("plc_coil_reset", "plc_io", "PLC I/O/Reset Command", "Reset Command", DataType.BOOL, 1,
                        "Writable reset command", "COIL", Access.READ_WRITE),
                register("plc_input_running", "plc_io", "PLC I/O/Running", "Running", DataType.BOOL, 0,
                        "Read-only running state", "DISCRETE_INPUT", Access.READ),
                register("plc_input_fault", "plc_io", "PLC I/O/Fault", "Fault", DataType.BOOL, 1,
                        "Read-only fault state", "DISCRETE_INPUT", Access.READ),
                register("plc_setpoint", "plc_io", "PLC I/O/Setpoint", "Setpoint", DataType.INT16, 0,
                        "Writable process setpoint", HOLDING_REGISTER, Access.READ_WRITE),
                register("plc_actual", "plc_io", "PLC I/O/Actual Value", "Actual Value", DataType.INT16, 1,
                        "Read-only process value", HOLDING_REGISTER, Access.READ));
    }

    /** Returns all available Modbus device profiles, keyed by stable template name. */
    public static Map<String, List<SchemaNode>> allTemplates() {
        Map<String, List<SchemaNode>> templates = new LinkedHashMap<>();
        templates.put("sunspec_inverter", sunspecInverter());
        templates.put("generic_energy_meter", genericEnergyMeter());
        templates.put("generic_plc_io", genericPlcIo());
        return templates;
    }

    /** Returns available Modbus device-profile names in display order. */
    public static List<String> templateNames() {
        return List.copyOf(allTemplates().keySet());
    }

    private static SchemaNode folder(String nodeId, String parentId, String path, String name) {
        return new SchemaNode(nodeId, parentId, path, name, NodeKind.FOLDER, null, null, null, null, null,
                List.of(), null, List.of());
    }

    private static SchemaNode register(String nodeId, String parentId, String path, String name, DataType dataType,
            int address, String description) {
        return register(nodeId, parentId, path, name, dataType, address, description, HOLDING_REGISTER, Access.READ);
    }

    private static SchemaNode register(String nodeId, String parentId, String path, String name, DataType dataType,
            int address, String description, String registerKind, Access access) {
        return new SchemaNode(nodeId, parentId, path, name, NodeKind.VARIABLE, dataType, ValueRank.SCALAR,
                access, null, description, List.of(), null, List.of(), null, List.of(), List.of(), null,
                null, null, null, null, null, null, registerKind, address);
    }
}
