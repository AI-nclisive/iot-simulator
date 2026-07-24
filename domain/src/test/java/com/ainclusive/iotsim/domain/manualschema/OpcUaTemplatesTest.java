package com.ainclusive.iotsim.domain.manualschema;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.ReferenceType;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpcUaTemplatesTest {

    @Test
    void pumpTemplateHasCorrectStructure() {
        var nodes = OpcUaTemplates.pump();

        assertThat(nodes).isNotEmpty();
        assertThat(nodes.stream().map(SchemaNode::nodeId)).contains(
                "pump_parent", "pump_flowrate", "pump_pressure", "pump_status", "pump_isrunning", "motor_parent");

        // Verify parent pump object
        var pumpParent = nodes.stream().filter(n -> n.nodeId().equals("pump_parent")).findFirst();
        assertThat(pumpParent).isPresent();
        assertThat(pumpParent.get().kind()).isEqualTo(NodeKind.OBJECT);
        assertThat(pumpParent.get().parentId()).isNull();
        assertThat(pumpParent.get().path()).isEqualTo("Pump");
        assertThat(pumpParent.get().references())
                .extracting(ref -> ref.targetNodeId())
                .contains("pump_flowrate", "pump_pressure", "pump_status", "pump_isrunning", "motor_parent");

        // Verify HAS_COMPONENT references are present
        assertThat(pumpParent.get().references())
                .allMatch(ref -> ref.type() == ReferenceType.HAS_COMPONENT);

        // Verify motor sub-device is included
        assertThat(nodes.stream().filter(n -> n.nodeId().equals("motor_parent")).findFirst()).isPresent();
    }

    @Test
    void pumpTemplateVariablesHaveCorrectDataTypes() {
        var nodes = OpcUaTemplates.pump();

        // FlowRate should be FLOAT64
        var flowrate = nodes.stream().filter(n -> n.nodeId().equals("pump_flowrate")).findFirst();
        assertThat(flowrate).isPresent();
        assertThat(flowrate.get().dataType()).isEqualTo(DataType.FLOAT64);
        assertThat(flowrate.get().access()).isEqualTo(Access.READ);
        assertThat(flowrate.get().unit()).isEqualTo("m3/h");

        // Status should be INT32
        var status = nodes.stream().filter(n -> n.nodeId().equals("pump_status")).findFirst();
        assertThat(status).isPresent();
        assertThat(status.get().dataType()).isEqualTo(DataType.INT32);

        // IsRunning should be BOOL with READ_WRITE access
        var isrunning = nodes.stream().filter(n -> n.nodeId().equals("pump_isrunning")).findFirst();
        assertThat(isrunning).isPresent();
        assertThat(isrunning.get().dataType()).isEqualTo(DataType.BOOL);
        assertThat(isrunning.get().access()).isEqualTo(Access.READ_WRITE);
    }

    @Test
    void pumpTemplateHasOpcUaAttributesSet() {
        var nodes = OpcUaTemplates.pump();

        // FlowRate should have minimumSamplingInterval
        var flowrate = nodes.stream().filter(n -> n.nodeId().equals("pump_flowrate")).findFirst();
        assertThat(flowrate).isPresent();
        assertThat(flowrate.get().minimumSamplingInterval()).isEqualTo(500);
        assertThat(flowrate.get().accessLevelFull()).isEqualTo(3);  // READ access level

        // IsRunning should be writable
        var isrunning = nodes.stream().filter(n -> n.nodeId().equals("pump_isrunning")).findFirst();
        assertThat(isrunning).isPresent();
        assertThat(isrunning.get().accessLevelFull()).isEqualTo(3);
        assertThat(isrunning.get().writeMask()).isEqualTo(3);  // Writable attributes
    }

    @Test
    void pumpTemplateParentIdsLinkCorrectly() {
        var nodes = OpcUaTemplates.pump();

        // All children should link to pump_parent except motor which links at component level
        var pumpChildren = nodes.stream().filter(n -> n.parentId() != null && n.parentId().equals("pump_parent")).toList();
        assertThat(pumpChildren).hasSizeGreaterThanOrEqualTo(5);  // FlowRate, Pressure, Status, IsRunning, Motor

        // Verify motor is correctly linked as a sub-component
        var motor = nodes.stream().filter(n -> n.nodeId().equals("motor_parent")).findFirst();
        assertThat(motor).isPresent();
        assertThat(motor.get().parentId()).isEqualTo("pump_parent");  // Motor is a HAS_COMPONENT child of pump
    }

    @Test
    void deviceIdentityTemplateHasCorrectStructure() {
        var nodes = OpcUaTemplates.deviceIdentity();

        assertThat(nodes).isNotEmpty();
        assertThat(nodes.stream().map(SchemaNode::nodeId)).contains(
                "deviceidentity_parent", "deviceidentity_vendor", "deviceidentity_model",
                "deviceidentity_serialnumber", "deviceidentity_firmware", "deviceidentity_macaddress");

        // Verify parent object
        var parent = nodes.stream().filter(n -> n.nodeId().equals("deviceidentity_parent")).findFirst();
        assertThat(parent).isPresent();
        assertThat(parent.get().kind()).isEqualTo(NodeKind.OBJECT);
        assertThat(parent.get().path()).isEqualTo("DeviceInfo");

        // All properties should use HAS_PROPERTY reference
        assertThat(parent.get().references())
                .allMatch(ref -> ref.type() == ReferenceType.HAS_PROPERTY);
    }

    @Test
    void deviceIdentityPropertiesAreReadOnly() {
        var nodes = OpcUaTemplates.deviceIdentity();

        var vendor = nodes.stream().filter(n -> n.nodeId().equals("deviceidentity_vendor")).findFirst();
        assertThat(vendor).isPresent();
        assertThat(vendor.get().dataType()).isEqualTo(DataType.STRING);
        assertThat(vendor.get().access()).isEqualTo(Access.READ);
        assertThat(vendor.get().accessLevelFull()).isEqualTo(1);  // READ-only

        var model = nodes.stream().filter(n -> n.nodeId().equals("deviceidentity_model")).findFirst();
        assertThat(model).isPresent();
        assertThat(model.get().dataType()).isEqualTo(DataType.STRING);
        assertThat(model.get().access()).isEqualTo(Access.READ);
    }

    @Test
    void temperatureSensorTemplateHasCorrectStructure() {
        var nodes = OpcUaTemplates.temperatureSensor();

        assertThat(nodes).isNotEmpty();
        assertThat(nodes.stream().map(SchemaNode::nodeId)).contains(
                "tempsensor_parent", "tempsensor_value", "tempsensor_minrange",
                "tempsensor_maxrange", "tempsensor_unit");

        // Verify parent object
        var parent = nodes.stream().filter(n -> n.nodeId().equals("tempsensor_parent")).findFirst();
        assertThat(parent).isPresent();
        assertThat(parent.get().path()).isEqualTo("TemperatureSensor");
        assertThat(parent.get().references())
                .extracting(ref -> ref.targetNodeId())
                .contains("tempsensor_value", "tempsensor_minrange", "tempsensor_maxrange", "tempsensor_unit");
    }

    @Test
    void temperatureSensorValueHasHistorizing() {
        var nodes = OpcUaTemplates.temperatureSensor();

        var value = nodes.stream().filter(n -> n.nodeId().equals("tempsensor_value")).findFirst();
        assertThat(value).isPresent();
        assertThat(value.get().dataType()).isEqualTo(DataType.FLOAT64);
        assertThat(value.get().unit()).isEqualTo("degC");
        assertThat(value.get().historizing()).isTrue();  // Should be historized
        assertThat(value.get().minimumSamplingInterval()).isEqualTo(2000);
    }

    @Test
    void temperatureSensorHasEURange() {
        var nodes = OpcUaTemplates.temperatureSensor();

        var value = nodes.stream().filter(n -> n.nodeId().equals("tempsensor_value")).findFirst();
        assertThat(value).isPresent();
        assertThat(value.get().references())
                .extracting(ref -> ref.targetNodeId())
                .contains("tempsensor_value_eurange");

        var eurange = nodes.stream().filter(n -> n.nodeId().equals("tempsensor_value_eurange")).findFirst();
        assertThat(eurange).isPresent();
        assertThat(eurange.get().parentId()).isEqualTo("tempsensor_value");
    }

    @Test
    void complexPumpStationTemplateHasMultipleComponents() {
        var nodes = OpcUaTemplates.complexPumpStation();

        assertThat(nodes).isNotEmpty();
        assertThat(nodes.stream().map(SchemaNode::nodeId)).contains(
                "pumpstation_parent", "pumpstation_pump1", "pumpstation_pump2",
                "pumpstation_valve1", "pumpstation_valve2", "pumpstation_pressuresensor");

        // Verify parent station
        var parent = nodes.stream().filter(n -> n.nodeId().equals("pumpstation_parent")).findFirst();
        assertThat(parent).isPresent();
        assertThat(parent.get().path()).isEqualTo("PumpStation");
        assertThat(parent.get().references()).hasSize(5);  // 2 pumps + 2 valves + 1 sensor

        // Verify pump1 has flowrate and pressure
        assertThat(nodes.stream().map(SchemaNode::nodeId))
                .contains("pumpstation_pump1_flowrate", "pumpstation_pump1_pressure");

        // Verify valve1 has position (writable)
        var valve1pos = nodes.stream().filter(n -> n.nodeId().equals("pumpstation_valve1_position")).findFirst();
        assertThat(valve1pos).isPresent();
        assertThat(valve1pos.get().access()).isEqualTo(Access.READ_WRITE);
    }

    @Test
    void complexPumpStationHierarchy() {
        var nodes = OpcUaTemplates.complexPumpStation();

        // Verify pump1 children link correctly
        var pump1 = nodes.stream().filter(n -> n.nodeId().equals("pumpstation_pump1")).findFirst();
        assertThat(pump1).isPresent();
        assertThat(pump1.get().parentId()).isEqualTo("pumpstation_parent");

        var pump1flowrate = nodes.stream().filter(n -> n.nodeId().equals("pumpstation_pump1_flowrate")).findFirst();
        assertThat(pump1flowrate).isPresent();
        assertThat(pump1flowrate.get().parentId()).isEqualTo("pumpstation_pump1");

        // Verify sensor is properly nested
        var sensor = nodes.stream().filter(n -> n.nodeId().equals("pumpstation_pressuresensor")).findFirst();
        assertThat(sensor).isPresent();
        assertThat(sensor.get().parentId()).isEqualTo("pumpstation_parent");

        var sensorval = nodes.stream().filter(n -> n.nodeId().equals("pumpstation_pressuresensor_value")).findFirst();
        assertThat(sensorval).isPresent();
        assertThat(sensorval.get().parentId()).isEqualTo("pumpstation_pressuresensor");
    }

    @Test
    void manufacturingCellTemplateHasCompleteHierarchy() {
        var nodes = OpcUaTemplates.manufacturingCell();

        assertThat(nodes).isNotEmpty();

        // Verify top-level components
        assertThat(nodes.stream().map(SchemaNode::nodeId)).contains(
                "mfgcell_parent", "mfgcell_robot", "mfgcell_conveyor", "mfgcell_inspector");

        // Verify robot hierarchy
        assertThat(nodes.stream().map(SchemaNode::nodeId)).contains(
                "mfgcell_robot_position", "mfgcell_robot_state", "mfgcell_robot_cyclecount");

        // Verify conveyor hierarchy
        assertThat(nodes.stream().map(SchemaNode::nodeId)).contains(
                "mfgcell_conveyor_speed", "mfgcell_conveyor_running");

        // Verify inspector hierarchy
        assertThat(nodes.stream().map(SchemaNode::nodeId)).contains(
                "mfgcell_inspector_status", "mfgcell_inspector_acceptcount", "mfgcell_inspector_rejectcount");
    }

    @Test
    void manufacturingCellPathsAreCorrect() {
        var nodes = OpcUaTemplates.manufacturingCell();

        var robot = nodes.stream().filter(n -> n.nodeId().equals("mfgcell_robot")).findFirst();
        assertThat(robot).isPresent();
        assertThat(robot.get().path()).isEqualTo("ManufacturingCell/Robot");

        var robotPos = nodes.stream().filter(n -> n.nodeId().equals("mfgcell_robot_position")).findFirst();
        assertThat(robotPos).isPresent();
        assertThat(robotPos.get().path()).isEqualTo("ManufacturingCell/Robot/Position");

        var inspector = nodes.stream().filter(n -> n.nodeId().equals("mfgcell_inspector")).findFirst();
        assertThat(inspector).isPresent();
        assertThat(inspector.get().path()).isEqualTo("ManufacturingCell/InspectionStation");

        var inspectorAccept = nodes.stream().filter(n -> n.nodeId().equals("mfgcell_inspector_acceptcount")).findFirst();
        assertThat(inspectorAccept).isPresent();
        assertThat(inspectorAccept.get().path()).isEqualTo("ManufacturingCell/InspectionStation/AcceptCount");
    }

    @Test
    void manufacturingCellWritableVariables() {
        var nodes = OpcUaTemplates.manufacturingCell();

        // Conveyor speed and running should be writable
        var speed = nodes.stream().filter(n -> n.nodeId().equals("mfgcell_conveyor_speed")).findFirst();
        assertThat(speed).isPresent();
        assertThat(speed.get().access()).isEqualTo(Access.READ_WRITE);
        assertThat(speed.get().writeMask()).isEqualTo(3);

        var running = nodes.stream().filter(n -> n.nodeId().equals("mfgcell_conveyor_running")).findFirst();
        assertThat(running).isPresent();
        assertThat(running.get().access()).isEqualTo(Access.READ_WRITE);

        // Robot position and state should be read-only
        var position = nodes.stream().filter(n -> n.nodeId().equals("mfgcell_robot_position")).findFirst();
        assertThat(position).isPresent();
        assertThat(position.get().access()).isEqualTo(Access.READ);
    }

    @Test
    void allTemplatesMapReturnsAllFifteenTemplates() {
        var templates = OpcUaTemplates.allTemplates();

        assertThat(templates).hasSize(15);
        assertThat(templates.keySet()).containsExactlyInAnyOrder(
                // Industrial Devices (5)
                "pump", "motor", "valve", "tank", "conveyor",
                // Standards (3)
                "packml_state_machine", "alarm_handler", "device_identity",
                // Sensors (4)
                "temperature_sensor", "pressure_sensor", "flow_meter", "level_gauge",
                // Advanced (3)
                "multi_tank_system", "complex_pump_station", "manufacturing_cell"
        );
    }

    @Test
    void allTemplatesReturnValidSchemaNodes() {
        var templates = OpcUaTemplates.allTemplates();

        for (Map.Entry<String, List<SchemaNode>> template : templates.entrySet()) {
            assertThat(template.getValue())
                    .as("Template '%s' should have nodes", template.getKey())
                    .isNotEmpty();

            // Verify all nodes have required fields
            for (SchemaNode node : template.getValue()) {
                assertThat(node.nodeId()).isNotBlank();
                assertThat(node.path()).isNotBlank();
                assertThat(node.name()).isNotBlank();
                assertThat(node.kind()).isNotNull();

                // Variables must have dataType or dataTypeNodeId and valueRank/access
                if (node.kind() == NodeKind.VARIABLE) {
                    assertThat(node.dataType() != null || node.dataTypeNodeId() != null)
                            .as("Template '%s' variable '%s' must have dataType or dataTypeNodeId",
                                    template.getKey(), node.nodeId())
                            .isTrue();
                    assertThat(node.valueRank()).isNotNull();
                    assertThat(node.access()).isNotNull();
                }
            }
        }
    }

    @Test
    void templateNamesListCompleteness() {
        var names = OpcUaTemplates.templateNames();

        assertThat(names).hasSize(15);
        assertThat(names).containsExactly(
                "pump", "motor", "valve", "tank", "conveyor",
                "packml_state_machine", "alarm_handler", "device_identity",
                "temperature_sensor", "pressure_sensor", "flow_meter", "level_gauge",
                "multi_tank_system", "complex_pump_station", "manufacturing_cell"
        );
    }

    @Test
    void motorTemplateStandsAlone() {
        var nodes = OpcUaTemplates.motor();

        assertThat(nodes).isNotEmpty();
        assertThat(nodes.stream().map(SchemaNode::nodeId)).contains(
                "motor_parent", "motor_speed", "motor_current", "motor_temperature", "motor_mode");

        var parent = nodes.stream().filter(n -> n.nodeId().equals("motor_parent")).findFirst();
        assertThat(parent).isPresent();
        assertThat(parent.get().references()).hasSize(4);
    }

    @Test
    void valveTemplateHasRangeProperty() {
        var nodes = OpcUaTemplates.valve();

        // Verify valve position has EURange property
        var position = nodes.stream().filter(n -> n.nodeId().equals("valve_position")).findFirst();
        assertThat(position).isPresent();
        assertThat(position.get().references())
                .extracting(ref -> ref.targetNodeId())
                .contains("valve_position_eurange");

        var eurange = nodes.stream().filter(n -> n.nodeId().equals("valve_position_eurange")).findFirst();
        assertThat(eurange).isPresent();
        assertThat(eurange.get().dataType()).isEqualTo(DataType.STRING);
    }

    @Test
    void tankTemplateHasNestedSensor() {
        var nodes = OpcUaTemplates.tank();

        assertThat(nodes.stream().map(SchemaNode::nodeId)).contains(
                "tank_parent", "tank_level", "tank_temperature", "tank_sensor", "tank_sensor_reading");

        var sensor = nodes.stream().filter(n -> n.nodeId().equals("tank_sensor")).findFirst();
        assertThat(sensor).isPresent();
        assertThat(sensor.get().parentId()).isEqualTo("tank_parent");

        var reading = nodes.stream().filter(n -> n.nodeId().equals("tank_sensor_reading")).findFirst();
        assertThat(reading).isPresent();
        assertThat(reading.get().parentId()).isEqualTo("tank_sensor");
    }

    @Test
    void alarmHandlerHasMethodNode() {
        var nodes = OpcUaTemplates.alarmHandler();

        var ackalarm = nodes.stream().filter(n -> n.nodeId().equals("alarmhandler_ackalarm")).findFirst();
        assertThat(ackalarm).isPresent();
        assertThat(ackalarm.get().kind()).isEqualTo(NodeKind.METHOD);
        assertThat(ackalarm.get().parentId()).isEqualTo("alarmhandler_parent");
    }

    @Test
    void alarmHandlerListIsArray() {
        var nodes = OpcUaTemplates.alarmHandler();

        var alarmlist = nodes.stream().filter(n -> n.nodeId().equals("alarmhandler_alarmlist")).findFirst();
        assertThat(alarmlist).isPresent();
        assertThat(alarmlist.get().dataType()).isEqualTo(DataType.STRING);
        assertThat(alarmlist.get().valueRank()).isEqualTo(com.ainclusive.iotsim.protocolmodel.ValueRank.ARRAY);
        assertThat(alarmlist.get().arrayDimensions()).contains(100);
    }

    @Test
    void multiTankSystemHasThreeTanks() {
        var nodes = OpcUaTemplates.multiTankSystem();

        assertThat(nodes.stream().map(SchemaNode::nodeId))
                .contains("multitank_tank1", "multitank_tank2", "multitank_tank3");

        // Verify parent links to all three tanks
        var parent = nodes.stream().filter(n -> n.nodeId().equals("multitank_parent")).findFirst();
        assertThat(parent).isPresent();
        assertThat(parent.get().references())
                .extracting(ref -> ref.targetNodeId())
                .contains("multitank_tank1", "multitank_tank2", "multitank_tank3");
    }

    @Test
    void multiTankSystemEachTankHasLevelAndTemperature() {
        var nodes = OpcUaTemplates.multiTankSystem();

        for (int i = 1; i <= 3; i++) {
            final int tankNum = i;
            var tank = nodes.stream()
                    .filter(n -> n.nodeId().equals("multitank_tank" + tankNum))
                    .findFirst();
            assertThat(tank).isPresent();

            var level = nodes.stream()
                    .filter(n -> n.nodeId().equals("multitank_tank" + tankNum + "_level"))
                    .findFirst();
            assertThat(level).isPresent();
            assertThat(level.get().parentId()).isEqualTo("multitank_tank" + tankNum);

            var temp = nodes.stream()
                    .filter(n -> n.nodeId().equals("multitank_tank" + tankNum + "_temp"))
                    .findFirst();
            assertThat(temp).isPresent();
            assertThat(temp.get().parentId()).isEqualTo("multitank_tank" + tankNum);
        }
    }
}
