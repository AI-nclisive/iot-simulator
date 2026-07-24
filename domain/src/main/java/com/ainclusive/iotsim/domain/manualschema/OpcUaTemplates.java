package com.ainclusive.iotsim.domain.manualschema;

import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.ReferenceType;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.SchemaReference;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reusable OPC UA device templates for manual schema creation.
 * Each template returns a List<SchemaNode> that can be instantiated in manual schemas
 * with proper hierarchical structure, references, and OPC UA attributes.
 */
public final class OpcUaTemplates {
    private OpcUaTemplates() {}

    // ==================== INDUSTRIAL DEVICES (5) ====================

    /**
     * Pump device template with flow rate, pressure, status, and motor sub-device.
     * Structure: Pump (parent) -> {FlowRate, Pressure, Status, IsRunning, Motor}
     */
    public static List<SchemaNode> pump() {
        List<SchemaNode> nodes = new ArrayList<>();

        // Parent pump object
        nodes.add(new SchemaNode(
                "pump_parent", null, "Pump", "Pump",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("pump_flowrate", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("pump_pressure", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("pump_status", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("pump_isrunning", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("motor_parent", ReferenceType.HAS_COMPONENT, true)
                )));

        // Flow rate (FLOAT64) with sampling interval
        nodes.add(new SchemaNode(
                "pump_flowrate", "pump_parent", "Pump/FlowRate", "FlowRate",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "m3/h", "Pump flow rate",
                List.of(), null, List.of(
                        new SchemaReference("pump_flowrate_eurange", ReferenceType.HAS_PROPERTY, true)
                ),
                null, List.of(),
                3, 500, null, null  // AccessLevel: READ, MinSamplingInterval: 500ms
        ));

        // EU Range property for FlowRate
        nodes.add(new SchemaNode(
                "pump_flowrate_eurange", "pump_flowrate", "Pump/FlowRate/EURange", "EURange",
                NodeKind.VARIABLE, DataType.STRING, ValueRank.SCALAR, Access.READ, null, null,
                List.of(), null, List.of()));

        // Pressure (FLOAT64)
        nodes.add(new SchemaNode(
                "pump_pressure", "pump_parent", "Pump/Pressure", "Pressure",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "bar", "Pump pressure",
                List.of(), null, List.of(),
                null, List.of(),
                3, 1000, null, null
        ));

        // Status (INT32)
        nodes.add(new SchemaNode(
                "pump_status", "pump_parent", "Pump/Status", "Status",
                NodeKind.VARIABLE, DataType.INT32, ValueRank.SCALAR, Access.READ, null, "Pump status code",
                List.of(), null, List.of(),
                null, List.of(),
                3, 2000, null, null
        ));

        // IsRunning (BOOL)
        nodes.add(new SchemaNode(
                "pump_isrunning", "pump_parent", "Pump/IsRunning", "IsRunning",
                NodeKind.VARIABLE, DataType.BOOL, ValueRank.SCALAR, Access.READ_WRITE, null, "Pump running state",
                List.of(), null, List.of(),
                null, List.of(),
                3, 500, 3, null
        ));

        // Motor sub-device (with pump_parent as parentId)
        var motorNodes = motorAsSubdevice("pump_parent");
        nodes.addAll(motorNodes);

        return nodes;
    }

    /**
     * Motor device template with speed, current, temperature, and mode.
     * Structure: Motor -> {Speed, Current, Temperature, Mode}
     */
    public static List<SchemaNode> motor() {
        return motorAsSubdevice(null);
    }

    /**
     * Helper method to create motor nodes with optional parent.
     */
    private static List<SchemaNode> motorAsSubdevice(String parentId) {
        List<SchemaNode> nodes = new ArrayList<>();

        // Parent motor object
        nodes.add(new SchemaNode(
                "motor_parent", parentId,
                parentId != null ? parentId + "/Motor" : "Motor",
                "Motor",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("motor_speed", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("motor_current", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("motor_temperature", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("motor_mode", ReferenceType.HAS_COMPONENT, true)
                )));

        // Speed (FLOAT64)
        nodes.add(new SchemaNode(
                "motor_speed", "motor_parent",
                (parentId != null ? parentId + "/" : "") + "Motor/Speed",
                "Speed",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "RPM", "Motor speed",
                List.of(), null, List.of(),
                null, List.of(),
                3, 500, null, null
        ));

        // Current (FLOAT64)
        nodes.add(new SchemaNode(
                "motor_current", "motor_parent",
                (parentId != null ? parentId + "/" : "") + "Motor/Current",
                "Current",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "A", "Motor current draw",
                List.of(), null, List.of(),
                null, List.of(),
                3, 1000, null, null
        ));

        // Temperature (FLOAT64)
        nodes.add(new SchemaNode(
                "motor_temperature", "motor_parent",
                (parentId != null ? parentId + "/" : "") + "Motor/Temperature",
                "Temperature",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", "Motor temperature",
                List.of(), null, List.of(),
                null, List.of(),
                3, 5000, null, null
        ));

        // Mode (INT32)
        nodes.add(new SchemaNode(
                "motor_mode", "motor_parent",
                (parentId != null ? parentId + "/" : "") + "Motor/Mode",
                "Mode",
                NodeKind.VARIABLE, DataType.INT32, ValueRank.SCALAR, Access.READ_WRITE, null, "Motor operating mode",
                List.of(), null, List.of(),
                null, List.of(),
                3, 2000, 3, null
        ));

        return nodes;
    }

    /**
     * Valve device template with position, pressure, and state.
     * Structure: Valve -> {Position, Pressure, State}
     */
    public static List<SchemaNode> valve() {
        List<SchemaNode> nodes = new ArrayList<>();

        nodes.add(new SchemaNode(
                "valve_parent", null, "Valve", "Valve",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("valve_position", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("valve_pressure", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("valve_state", ReferenceType.HAS_COMPONENT, true)
                )));

        // Position (FLOAT64, 0-100%)
        nodes.add(new SchemaNode(
                "valve_position", "valve_parent", "Valve/Position", "Position",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ_WRITE, "%", "Valve opening percentage",
                List.of(), null, List.of(
                        new SchemaReference("valve_position_eurange", ReferenceType.HAS_PROPERTY, true)
                ),
                null, List.of(),
                15, 500, 3, null
        ));

        // Position EURange property
        nodes.add(new SchemaNode(
                "valve_position_eurange", "valve_position", "Valve/Position/EURange", "EURange",
                NodeKind.VARIABLE, DataType.STRING, ValueRank.SCALAR, Access.READ, null, null,
                List.of(), null, List.of()));

        // Pressure (FLOAT64)
        nodes.add(new SchemaNode(
                "valve_pressure", "valve_parent", "Valve/Pressure", "Pressure",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "bar", "Valve inlet pressure",
                List.of(), null, List.of(),
                null, List.of(),
                3, 1000, null, null
        ));

        // State (INT32)
        nodes.add(new SchemaNode(
                "valve_state", "valve_parent", "Valve/State", "State",
                NodeKind.VARIABLE, DataType.INT32, ValueRank.SCALAR, Access.READ, null, "Valve state (Open=1, Closed=0)",
                List.of(), null, List.of(),
                null, List.of(),
                3, 500, null, null
        ));

        return nodes;
    }

    /**
     * Tank device template with level, temperature, and sensor sub-device.
     * Structure: Tank -> {Level, Temperature, Sensor}
     */
    public static List<SchemaNode> tank() {
        List<SchemaNode> nodes = new ArrayList<>();

        nodes.add(new SchemaNode(
                "tank_parent", null, "Tank", "Tank",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("tank_level", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("tank_temperature", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("tank_sensor", ReferenceType.HAS_COMPONENT, true)
                )));

        // Level (FLOAT64, percentage)
        nodes.add(new SchemaNode(
                "tank_level", "tank_parent", "Tank/Level", "Level",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "%", "Tank fill level",
                List.of(), null, List.of(),
                null, List.of(),
                3, 2000, null, null
        ));

        // Temperature (FLOAT64)
        nodes.add(new SchemaNode(
                "tank_temperature", "tank_parent", "Tank/Temperature", "Temperature",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", "Tank temperature",
                List.of(), null, List.of(),
                null, List.of(),
                3, 5000, null, null
        ));

        // Sensor sub-device
        nodes.add(new SchemaNode(
                "tank_sensor", "tank_parent", "Tank/Sensor", "Sensor",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("tank_sensor_reading", ReferenceType.HAS_COMPONENT, true)
                )));

        nodes.add(new SchemaNode(
                "tank_sensor_reading", "tank_sensor", "Tank/Sensor/Reading", "Reading",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "%", "Raw sensor reading",
                List.of(), null, List.of(),
                null, List.of(),
                3, 1000, null, null
        ));

        return nodes;
    }

    /**
     * Conveyor device template with speed, motor current, count, and running state.
     * Structure: Conveyor -> {Speed, MotorCurrent, Count, Running}
     */
    public static List<SchemaNode> conveyor() {
        List<SchemaNode> nodes = new ArrayList<>();

        nodes.add(new SchemaNode(
                "conveyor_parent", null, "Conveyor", "Conveyor",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("conveyor_speed", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("conveyor_motorcurrent", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("conveyor_count", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("conveyor_running", ReferenceType.HAS_COMPONENT, true)
                )));

        // Speed (FLOAT64)
        nodes.add(new SchemaNode(
                "conveyor_speed", "conveyor_parent", "Conveyor/Speed", "Speed",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ_WRITE, "m/s", "Conveyor belt speed",
                List.of(), null, List.of(),
                null, List.of(),
                15, 500, 3, null
        ));

        // Motor Current (FLOAT64)
        nodes.add(new SchemaNode(
                "conveyor_motorcurrent", "conveyor_parent", "Conveyor/MotorCurrent", "MotorCurrent",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "A", "Motor current draw",
                List.of(), null, List.of(),
                null, List.of(),
                3, 1000, null, null
        ));

        // Count (INT32)
        nodes.add(new SchemaNode(
                "conveyor_count", "conveyor_parent", "Conveyor/Count", "Count",
                NodeKind.VARIABLE, DataType.INT32, ValueRank.SCALAR, Access.READ, null, "Parts counted on belt",
                List.of(), null, List.of(),
                null, List.of(),
                3, 500, null, null
        ));

        // Running (BOOL)
        nodes.add(new SchemaNode(
                "conveyor_running", "conveyor_parent", "Conveyor/Running", "Running",
                NodeKind.VARIABLE, DataType.BOOL, ValueRank.SCALAR, Access.READ_WRITE, null, "Conveyor running state",
                List.of(), null, List.of(),
                null, List.of(),
                15, 500, 3, null
        ));

        return nodes;
    }

    // ==================== STANDARDS (3) ====================

    /**
     * PackML State Machine template with StateManager and Mode selector.
     * Structure: StateManager -> {State, Mode}
     */
    public static List<SchemaNode> packmlStateMachine() {
        List<SchemaNode> nodes = new ArrayList<>();

        nodes.add(new SchemaNode(
                "packml_statemanager", null, "StateManager", "StateManager",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("packml_state", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("packml_mode", ReferenceType.HAS_COMPONENT, true)
                )));

        // Current state (INT32: Idle=0, Running=1, Error=2, etc.)
        nodes.add(new SchemaNode(
                "packml_state", "packml_statemanager", "StateManager/State", "State",
                NodeKind.VARIABLE, DataType.INT32, ValueRank.SCALAR, Access.READ, null,
                "Current state (Idle=0, Running=1, Error=2, Stopped=3, Paused=4)",
                List.of(), null, List.of(),
                null, List.of(),
                3, 1000, null, null
        ));

        // Mode selector (INT32)
        nodes.add(new SchemaNode(
                "packml_mode", "packml_statemanager", "StateManager/Mode", "Mode",
                NodeKind.VARIABLE, DataType.INT32, ValueRank.SCALAR, Access.READ_WRITE, null,
                "Operating mode (Manual=0, Automatic=1, Simulation=2)",
                List.of(), null, List.of(),
                null, List.of(),
                15, 500, 3, null
        ));

        return nodes;
    }

    /**
     * Alarm Handler template with AlarmList array, AckAlarm method, and Status.
     * Structure: Alarms -> {AlarmList, Status, AckAlarm method}
     */
    public static List<SchemaNode> alarmHandler() {
        List<SchemaNode> nodes = new ArrayList<>();

        nodes.add(new SchemaNode(
                "alarmhandler_parent", null, "Alarms", "Alarms",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("alarmhandler_alarmlist", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("alarmhandler_status", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("alarmhandler_ackalarm", ReferenceType.HAS_COMPONENT, true)
                )));

        // AlarmList (ARRAY of STRING)
        nodes.add(new SchemaNode(
                "alarmhandler_alarmlist", "alarmhandler_parent", "Alarms/AlarmList", "AlarmList",
                NodeKind.VARIABLE, DataType.STRING, ValueRank.ARRAY, Access.READ, null, "List of active alarms",
                List.of(100), null, List.of(),
                null, List.of(),
                3, 1000, null, null
        ));

        // Status (INT32: NoAlarm=0, Warning=1, Critical=2)
        nodes.add(new SchemaNode(
                "alarmhandler_status", "alarmhandler_parent", "Alarms/Status", "Status",
                NodeKind.VARIABLE, DataType.INT32, ValueRank.SCALAR, Access.READ, null,
                "Alarm status (NoAlarm=0, Warning=1, Critical=2)",
                List.of(), null, List.of(),
                null, List.of(),
                3, 500, null, null
        ));

        // AckAlarm method
        nodes.add(new SchemaNode(
                "alarmhandler_ackalarm", "alarmhandler_parent", "Alarms/AckAlarm", "AckAlarm",
                NodeKind.METHOD, null, null, null, null, "Acknowledge an alarm"));

        return nodes;
    }

    /**
     * Device Identity template with vendor info, model, serial number, firmware, and MAC address.
     * Structure: DeviceInfo -> {Vendor, Model, SerialNumber, FirmwareVersion, MacAddress}
     */
    public static List<SchemaNode> deviceIdentity() {
        List<SchemaNode> nodes = new ArrayList<>();

        nodes.add(new SchemaNode(
                "deviceidentity_parent", null, "DeviceInfo", "DeviceInfo",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("deviceidentity_vendor", ReferenceType.HAS_PROPERTY, true),
                        new SchemaReference("deviceidentity_model", ReferenceType.HAS_PROPERTY, true),
                        new SchemaReference("deviceidentity_serialnumber", ReferenceType.HAS_PROPERTY, true),
                        new SchemaReference("deviceidentity_firmware", ReferenceType.HAS_PROPERTY, true),
                        new SchemaReference("deviceidentity_macaddress", ReferenceType.HAS_PROPERTY, true)
                )));

        // Vendor (STRING)
        nodes.add(new SchemaNode(
                "deviceidentity_vendor", "deviceidentity_parent", "DeviceInfo/Vendor", "Vendor",
                NodeKind.VARIABLE, DataType.STRING, ValueRank.SCALAR, Access.READ, null, "Device vendor name",
                List.of(), null, List.of(),
                null, List.of(),
                1, 5000, null, null
        ));

        // Model (STRING)
        nodes.add(new SchemaNode(
                "deviceidentity_model", "deviceidentity_parent", "DeviceInfo/Model", "Model",
                NodeKind.VARIABLE, DataType.STRING, ValueRank.SCALAR, Access.READ, null, "Device model number",
                List.of(), null, List.of(),
                null, List.of(),
                1, 5000, null, null
        ));

        // Serial Number (STRING)
        nodes.add(new SchemaNode(
                "deviceidentity_serialnumber", "deviceidentity_parent", "DeviceInfo/SerialNumber", "SerialNumber",
                NodeKind.VARIABLE, DataType.STRING, ValueRank.SCALAR, Access.READ, null, "Device serial number",
                List.of(), null, List.of(),
                null, List.of(),
                1, 5000, null, null
        ));

        // Firmware Version (STRING)
        nodes.add(new SchemaNode(
                "deviceidentity_firmware", "deviceidentity_parent", "DeviceInfo/FirmwareVersion", "FirmwareVersion",
                NodeKind.VARIABLE, DataType.STRING, ValueRank.SCALAR, Access.READ, null, "Device firmware version",
                List.of(), null, List.of(),
                null, List.of(),
                1, 5000, null, null
        ));

        // MAC Address (STRING)
        nodes.add(new SchemaNode(
                "deviceidentity_macaddress", "deviceidentity_parent", "DeviceInfo/MacAddress", "MacAddress",
                NodeKind.VARIABLE, DataType.STRING, ValueRank.SCALAR, Access.READ, null, "Device MAC address",
                List.of(), null, List.of(),
                null, List.of(),
                1, 5000, null, null
        ));

        return nodes;
    }

    // ==================== SENSORS (4) ====================

    /**
     * Temperature Sensor template with value, range, and unit property.
     * Structure: TemperatureSensor -> {Value, MinRange, MaxRange, Unit}
     */
    public static List<SchemaNode> temperatureSensor() {
        List<SchemaNode> nodes = new ArrayList<>();

        nodes.add(new SchemaNode(
                "tempsensor_parent", null, "TemperatureSensor", "TemperatureSensor",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("tempsensor_value", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("tempsensor_minrange", ReferenceType.HAS_PROPERTY, true),
                        new SchemaReference("tempsensor_maxrange", ReferenceType.HAS_PROPERTY, true),
                        new SchemaReference("tempsensor_unit", ReferenceType.HAS_PROPERTY, true)
                )));

        // Sensor value (FLOAT64)
        nodes.add(new SchemaNode(
                "tempsensor_value", "tempsensor_parent", "TemperatureSensor/Value", "Value",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", "Temperature reading",
                List.of(), null, List.of(
                        new SchemaReference("tempsensor_value_eurange", ReferenceType.HAS_PROPERTY, true)
                ),
                null, List.of(),
                3, 2000, null, true  // historizing=true
        ));

        // EURange property
        nodes.add(new SchemaNode(
                "tempsensor_value_eurange", "tempsensor_value", "TemperatureSensor/Value/EURange", "EURange",
                NodeKind.VARIABLE, DataType.STRING, ValueRank.SCALAR, Access.READ, null, null,
                List.of(), null, List.of(),
                null, List.of(),
                1, 5000, null, null
        ));

        // Min Range (FLOAT64)
        nodes.add(new SchemaNode(
                "tempsensor_minrange", "tempsensor_parent", "TemperatureSensor/MinRange", "MinRange",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", "Minimum measurement range",
                List.of(), null, List.of(),
                null, List.of(),
                1, 5000, null, null
        ));

        // Max Range (FLOAT64)
        nodes.add(new SchemaNode(
                "tempsensor_maxrange", "tempsensor_parent", "TemperatureSensor/MaxRange", "MaxRange",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", "Maximum measurement range",
                List.of(), null, List.of(),
                null, List.of(),
                1, 5000, null, null
        ));

        // Unit (STRING)
        nodes.add(new SchemaNode(
                "tempsensor_unit", "tempsensor_parent", "TemperatureSensor/Unit", "Unit",
                NodeKind.VARIABLE, DataType.STRING, ValueRank.SCALAR, Access.READ, null, "Measurement unit",
                List.of(), null, List.of(),
                null, List.of(),
                1, 5000, null, null
        ));

        return nodes;
    }

    /**
     * Pressure Sensor template with value, unit, and accuracy.
     * Structure: PressureSensor -> {Value, Unit, Accuracy}
     */
    public static List<SchemaNode> pressureSensor() {
        List<SchemaNode> nodes = new ArrayList<>();

        nodes.add(new SchemaNode(
                "pressuresensor_parent", null, "PressureSensor", "PressureSensor",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("pressuresensor_value", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("pressuresensor_unit", ReferenceType.HAS_PROPERTY, true),
                        new SchemaReference("pressuresensor_accuracy", ReferenceType.HAS_PROPERTY, true)
                )));

        // Pressure value (FLOAT64)
        nodes.add(new SchemaNode(
                "pressuresensor_value", "pressuresensor_parent", "PressureSensor/Value", "Value",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "bar", "Pressure reading",
                List.of(), null, List.of(),
                null, List.of(),
                3, 1000, null, true
        ));

        // Unit (STRING)
        nodes.add(new SchemaNode(
                "pressuresensor_unit", "pressuresensor_parent", "PressureSensor/Unit", "Unit",
                NodeKind.VARIABLE, DataType.STRING, ValueRank.SCALAR, Access.READ, null, "Measurement unit",
                List.of(), null, List.of(),
                null, List.of(),
                1, 5000, null, null
        ));

        // Accuracy (FLOAT64)
        nodes.add(new SchemaNode(
                "pressuresensor_accuracy", "pressuresensor_parent", "PressureSensor/Accuracy", "Accuracy",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "%", "Sensor accuracy",
                List.of(), null, List.of(),
                null, List.of(),
                1, 5000, null, null
        ));

        return nodes;
    }

    /**
     * Flow Meter template with flow rate, total volume, and unit.
     * Structure: FlowMeter -> {FlowRate, TotalVolume, Unit}
     */
    public static List<SchemaNode> flowMeter() {
        List<SchemaNode> nodes = new ArrayList<>();

        nodes.add(new SchemaNode(
                "flowmeter_parent", null, "FlowMeter", "FlowMeter",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("flowmeter_flowrate", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("flowmeter_totalvolume", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("flowmeter_unit", ReferenceType.HAS_PROPERTY, true)
                )));

        // Flow rate (FLOAT64)
        nodes.add(new SchemaNode(
                "flowmeter_flowrate", "flowmeter_parent", "FlowMeter/FlowRate", "FlowRate",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "m3/h", "Current flow rate",
                List.of(), null, List.of(),
                null, List.of(),
                3, 500, null, true
        ));

        // Total Volume (FLOAT64)
        nodes.add(new SchemaNode(
                "flowmeter_totalvolume", "flowmeter_parent", "FlowMeter/TotalVolume", "TotalVolume",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "m3", "Cumulative volume",
                List.of(), null, List.of(),
                null, List.of(),
                3, 5000, null, true
        ));

        // Unit (STRING)
        nodes.add(new SchemaNode(
                "flowmeter_unit", "flowmeter_parent", "FlowMeter/Unit", "Unit",
                NodeKind.VARIABLE, DataType.STRING, ValueRank.SCALAR, Access.READ, null, "Measurement unit",
                List.of(), null, List.of(),
                null, List.of(),
                1, 5000, null, null
        ));

        return nodes;
    }

    /**
     * Level Gauge template with level percentage and alarm thresholds.
     * Structure: LevelGauge -> {Level, MinAlarm, MaxAlarm}
     */
    public static List<SchemaNode> levelGauge() {
        List<SchemaNode> nodes = new ArrayList<>();

        nodes.add(new SchemaNode(
                "levelgauge_parent", null, "LevelGauge", "LevelGauge",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("levelgauge_level", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("levelgauge_minalarm", ReferenceType.HAS_PROPERTY, true),
                        new SchemaReference("levelgauge_maxalarm", ReferenceType.HAS_PROPERTY, true)
                )));

        // Level percentage (FLOAT64)
        nodes.add(new SchemaNode(
                "levelgauge_level", "levelgauge_parent", "LevelGauge/Level", "Level",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "%", "Fill level percentage",
                List.of(), null, List.of(),
                null, List.of(),
                3, 2000, null, true
        ));

        // Min Alarm threshold (FLOAT64)
        nodes.add(new SchemaNode(
                "levelgauge_minalarm", "levelgauge_parent", "LevelGauge/MinAlarm", "MinAlarm",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ_WRITE, "%", "Low level alarm threshold",
                List.of(), null, List.of(),
                null, List.of(),
                15, 5000, 3, null
        ));

        // Max Alarm threshold (FLOAT64)
        nodes.add(new SchemaNode(
                "levelgauge_maxalarm", "levelgauge_parent", "LevelGauge/MaxAlarm", "MaxAlarm",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ_WRITE, "%", "High level alarm threshold",
                List.of(), null, List.of(),
                null, List.of(),
                15, 5000, 3, null
        ));

        return nodes;
    }

    // ==================== ADVANCED (3) ====================

    /**
     * Multi-Tank System with 3 tank sub-devices linked hierarchically.
     * Structure: MultiTankSystem -> {Tank1, Tank2, Tank3} with HAS_COMPONENT cross-links
     */
    public static List<SchemaNode> multiTankSystem() {
        List<SchemaNode> nodes = new ArrayList<>();

        // Parent system
        nodes.add(new SchemaNode(
                "multitank_parent", null, "MultiTankSystem", "MultiTankSystem",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("multitank_tank1", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("multitank_tank2", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("multitank_tank3", ReferenceType.HAS_COMPONENT, true)
                )));

        // Tank 1
        nodes.add(new SchemaNode(
                "multitank_tank1", "multitank_parent", "MultiTankSystem/Tank1", "Tank1",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("multitank_tank1_level", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("multitank_tank1_temp", ReferenceType.HAS_COMPONENT, true)
                )));

        nodes.add(new SchemaNode(
                "multitank_tank1_level", "multitank_tank1", "MultiTankSystem/Tank1/Level", "Level",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "%", "Tank 1 level",
                List.of(), null, List.of(),
                null, List.of(),
                3, 2000, null, null
        ));

        nodes.add(new SchemaNode(
                "multitank_tank1_temp", "multitank_tank1", "MultiTankSystem/Tank1/Temperature", "Temperature",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", "Tank 1 temperature",
                List.of(), null, List.of(),
                null, List.of(),
                3, 5000, null, null
        ));

        // Tank 2
        nodes.add(new SchemaNode(
                "multitank_tank2", "multitank_parent", "MultiTankSystem/Tank2", "Tank2",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("multitank_tank2_level", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("multitank_tank2_temp", ReferenceType.HAS_COMPONENT, true)
                )));

        nodes.add(new SchemaNode(
                "multitank_tank2_level", "multitank_tank2", "MultiTankSystem/Tank2/Level", "Level",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "%", "Tank 2 level",
                List.of(), null, List.of(),
                null, List.of(),
                3, 2000, null, null
        ));

        nodes.add(new SchemaNode(
                "multitank_tank2_temp", "multitank_tank2", "MultiTankSystem/Tank2/Temperature", "Temperature",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", "Tank 2 temperature",
                List.of(), null, List.of(),
                null, List.of(),
                3, 5000, null, null
        ));

        // Tank 3
        nodes.add(new SchemaNode(
                "multitank_tank3", "multitank_parent", "MultiTankSystem/Tank3", "Tank3",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("multitank_tank3_level", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("multitank_tank3_temp", ReferenceType.HAS_COMPONENT, true)
                )));

        nodes.add(new SchemaNode(
                "multitank_tank3_level", "multitank_tank3", "MultiTankSystem/Tank3/Level", "Level",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "%", "Tank 3 level",
                List.of(), null, List.of(),
                null, List.of(),
                3, 2000, null, null
        ));

        nodes.add(new SchemaNode(
                "multitank_tank3_temp", "multitank_tank3", "MultiTankSystem/Tank3/Temperature", "Temperature",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", "Tank 3 temperature",
                List.of(), null, List.of(),
                null, List.of(),
                3, 5000, null, null
        ));

        return nodes;
    }

    /**
     * Complex Pump Station with multiple pumps, valves, and pressure sensors.
     * Structure: PumpStation -> {Pump1, Pump2, Valve1, Valve2, PressureSensor}
     */
    public static List<SchemaNode> complexPumpStation() {
        List<SchemaNode> nodes = new ArrayList<>();

        // Parent station
        nodes.add(new SchemaNode(
                "pumpstation_parent", null, "PumpStation", "PumpStation",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("pumpstation_pump1", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("pumpstation_pump2", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("pumpstation_valve1", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("pumpstation_valve2", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("pumpstation_pressuresensor", ReferenceType.HAS_COMPONENT, true)
                )));

        // Pump 1
        nodes.add(new SchemaNode(
                "pumpstation_pump1", "pumpstation_parent", "PumpStation/Pump1", "Pump1",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("pumpstation_pump1_flowrate", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("pumpstation_pump1_pressure", ReferenceType.HAS_COMPONENT, true)
                )));

        nodes.add(new SchemaNode(
                "pumpstation_pump1_flowrate", "pumpstation_pump1", "PumpStation/Pump1/FlowRate", "FlowRate",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "m3/h", "Pump 1 flow rate",
                List.of(), null, List.of(),
                null, List.of(),
                3, 500, null, null
        ));

        nodes.add(new SchemaNode(
                "pumpstation_pump1_pressure", "pumpstation_pump1", "PumpStation/Pump1/Pressure", "Pressure",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "bar", "Pump 1 pressure",
                List.of(), null, List.of(),
                null, List.of(),
                3, 1000, null, null
        ));

        // Pump 2
        nodes.add(new SchemaNode(
                "pumpstation_pump2", "pumpstation_parent", "PumpStation/Pump2", "Pump2",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("pumpstation_pump2_flowrate", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("pumpstation_pump2_pressure", ReferenceType.HAS_COMPONENT, true)
                )));

        nodes.add(new SchemaNode(
                "pumpstation_pump2_flowrate", "pumpstation_pump2", "PumpStation/Pump2/FlowRate", "FlowRate",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "m3/h", "Pump 2 flow rate",
                List.of(), null, List.of(),
                null, List.of(),
                3, 500, null, null
        ));

        nodes.add(new SchemaNode(
                "pumpstation_pump2_pressure", "pumpstation_pump2", "PumpStation/Pump2/Pressure", "Pressure",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "bar", "Pump 2 pressure",
                List.of(), null, List.of(),
                null, List.of(),
                3, 1000, null, null
        ));

        // Valve 1
        nodes.add(new SchemaNode(
                "pumpstation_valve1", "pumpstation_parent", "PumpStation/Valve1", "Valve1",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("pumpstation_valve1_position", ReferenceType.HAS_COMPONENT, true)
                )));

        nodes.add(new SchemaNode(
                "pumpstation_valve1_position", "pumpstation_valve1", "PumpStation/Valve1/Position", "Position",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ_WRITE, "%", "Valve 1 opening",
                List.of(), null, List.of(),
                null, List.of(),
                15, 500, 3, null
        ));

        // Valve 2
        nodes.add(new SchemaNode(
                "pumpstation_valve2", "pumpstation_parent", "PumpStation/Valve2", "Valve2",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("pumpstation_valve2_position", ReferenceType.HAS_COMPONENT, true)
                )));

        nodes.add(new SchemaNode(
                "pumpstation_valve2_position", "pumpstation_valve2", "PumpStation/Valve2/Position", "Position",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ_WRITE, "%", "Valve 2 opening",
                List.of(), null, List.of(),
                null, List.of(),
                15, 500, 3, null
        ));

        // Pressure Sensor
        nodes.add(new SchemaNode(
                "pumpstation_pressuresensor", "pumpstation_parent", "PumpStation/PressureSensor", "PressureSensor",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("pumpstation_pressuresensor_value", ReferenceType.HAS_COMPONENT, true)
                )));

        nodes.add(new SchemaNode(
                "pumpstation_pressuresensor_value", "pumpstation_pressuresensor",
                "PumpStation/PressureSensor/Value", "Value",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "bar", "System outlet pressure",
                List.of(), null, List.of(),
                null, List.of(),
                3, 1000, null, null
        ));

        return nodes;
    }

    /**
     * Manufacturing Cell with robot, conveyor, and inspection station hierarchically linked.
     * Structure: ManufacturingCell -> {Robot, Conveyor, InspectionStation}
     */
    public static List<SchemaNode> manufacturingCell() {
        List<SchemaNode> nodes = new ArrayList<>();

        // Parent cell
        nodes.add(new SchemaNode(
                "mfgcell_parent", null, "ManufacturingCell", "ManufacturingCell",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("mfgcell_robot", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("mfgcell_conveyor", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("mfgcell_inspector", ReferenceType.HAS_COMPONENT, true)
                )));

        // Robot
        nodes.add(new SchemaNode(
                "mfgcell_robot", "mfgcell_parent", "ManufacturingCell/Robot", "Robot",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("mfgcell_robot_position", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("mfgcell_robot_state", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("mfgcell_robot_cyclecount", ReferenceType.HAS_COMPONENT, true)
                )));

        nodes.add(new SchemaNode(
                "mfgcell_robot_position", "mfgcell_robot", "ManufacturingCell/Robot/Position", "Position",
                NodeKind.VARIABLE, DataType.INT32, ValueRank.SCALAR, Access.READ, null, "Robot position code",
                List.of(), null, List.of(),
                null, List.of(),
                3, 500, null, null
        ));

        nodes.add(new SchemaNode(
                "mfgcell_robot_state", "mfgcell_robot", "ManufacturingCell/Robot/State", "State",
                NodeKind.VARIABLE, DataType.STRING, ValueRank.SCALAR, Access.READ, null, "Robot state",
                List.of(), null, List.of(),
                null, List.of(),
                3, 500, null, null
        ));

        nodes.add(new SchemaNode(
                "mfgcell_robot_cyclecount", "mfgcell_robot", "ManufacturingCell/Robot/CycleCount", "CycleCount",
                NodeKind.VARIABLE, DataType.INT32, ValueRank.SCALAR, Access.READ, null, "Parts processed",
                List.of(), null, List.of(),
                null, List.of(),
                3, 1000, null, null
        ));

        // Conveyor
        nodes.add(new SchemaNode(
                "mfgcell_conveyor", "mfgcell_parent", "ManufacturingCell/Conveyor", "Conveyor",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("mfgcell_conveyor_speed", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("mfgcell_conveyor_running", ReferenceType.HAS_COMPONENT, true)
                )));

        nodes.add(new SchemaNode(
                "mfgcell_conveyor_speed", "mfgcell_conveyor", "ManufacturingCell/Conveyor/Speed", "Speed",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ_WRITE, "m/s", "Belt speed",
                List.of(), null, List.of(),
                null, List.of(),
                15, 500, 3, null
        ));

        nodes.add(new SchemaNode(
                "mfgcell_conveyor_running", "mfgcell_conveyor", "ManufacturingCell/Conveyor/Running", "Running",
                NodeKind.VARIABLE, DataType.BOOL, ValueRank.SCALAR, Access.READ_WRITE, null, "Running state",
                List.of(), null, List.of(),
                null, List.of(),
                15, 500, 3, null
        ));

        // Inspection Station
        nodes.add(new SchemaNode(
                "mfgcell_inspector", "mfgcell_parent", "ManufacturingCell/InspectionStation", "InspectionStation",
                NodeKind.OBJECT, null, null, null, null, null,
                List.of(), null, List.of(
                        new SchemaReference("mfgcell_inspector_status", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("mfgcell_inspector_acceptcount", ReferenceType.HAS_COMPONENT, true),
                        new SchemaReference("mfgcell_inspector_rejectcount", ReferenceType.HAS_COMPONENT, true)
                )));

        nodes.add(new SchemaNode(
                "mfgcell_inspector_status", "mfgcell_inspector", "ManufacturingCell/InspectionStation/Status", "Status",
                NodeKind.VARIABLE, DataType.STRING, ValueRank.SCALAR, Access.READ, null, "Inspection status",
                List.of(), null, List.of(),
                null, List.of(),
                3, 500, null, null
        ));

        nodes.add(new SchemaNode(
                "mfgcell_inspector_acceptcount", "mfgcell_inspector", "ManufacturingCell/InspectionStation/AcceptCount", "AcceptCount",
                NodeKind.VARIABLE, DataType.INT32, ValueRank.SCALAR, Access.READ, null, "Accepted parts",
                List.of(), null, List.of(),
                null, List.of(),
                3, 1000, null, null
        ));

        nodes.add(new SchemaNode(
                "mfgcell_inspector_rejectcount", "mfgcell_inspector", "ManufacturingCell/InspectionStation/RejectCount", "RejectCount",
                NodeKind.VARIABLE, DataType.INT32, ValueRank.SCALAR, Access.READ, null, "Rejected parts",
                List.of(), null, List.of(),
                null, List.of(),
                3, 1000, null, null
        ));

        return nodes;
    }

    // ==================== EXPORT METHODS ====================

    /**
     * Returns a map of all template names to their node definitions.
     * Useful for factory creation and template discovery.
     */
    public static Map<String, List<SchemaNode>> allTemplates() {
        Map<String, List<SchemaNode>> templates = new HashMap<>();

        // Industrial Devices
        templates.put("pump", pump());
        templates.put("motor", motor());
        templates.put("valve", valve());
        templates.put("tank", tank());
        templates.put("conveyor", conveyor());

        // Standards
        templates.put("packml_state_machine", packmlStateMachine());
        templates.put("alarm_handler", alarmHandler());
        templates.put("device_identity", deviceIdentity());

        // Sensors
        templates.put("temperature_sensor", temperatureSensor());
        templates.put("pressure_sensor", pressureSensor());
        templates.put("flow_meter", flowMeter());
        templates.put("level_gauge", levelGauge());

        // Advanced
        templates.put("multi_tank_system", multiTankSystem());
        templates.put("complex_pump_station", complexPumpStation());
        templates.put("manufacturing_cell", manufacturingCell());

        return templates;
    }

    /**
     * Returns a list of all template names in order.
     */
    public static List<String> templateNames() {
        return List.of(
                // Industrial Devices
                "pump", "motor", "valve", "tank", "conveyor",
                // Standards
                "packml_state_machine", "alarm_handler", "device_identity",
                // Sensors
                "temperature_sensor", "pressure_sensor", "flow_meter", "level_gauge",
                // Advanced
                "multi_tank_system", "complex_pump_station", "manufacturing_cell"
        );
    }
}
