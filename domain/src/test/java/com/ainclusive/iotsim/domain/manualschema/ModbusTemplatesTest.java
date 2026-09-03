package com.ainclusive.iotsim.domain.manualschema;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ModbusTemplatesTest {

    @Test
    void catalogExposesAllBuiltInTemplates() {
        assertThat(ModbusTemplates.templateNames()).containsExactly(
                "sunspec_inverter", "generic_energy_meter", "generic_plc_io");
        assertThat(ModbusTemplates.allTemplates()).containsOnlyKeys(
                "sunspec_inverter", "generic_energy_meter", "generic_plc_io");
        assertThat(ModbusTemplates.allTemplates().get("sunspec_inverter")).isEqualTo(ModbusTemplates.sunspecInverter());
    }

    @Test
    void genericProfilesHaveExplicitBindingsInTheirOwnDataAreas() {
        Map<String, SchemaNode> meter = nodesById(ModbusTemplates.genericEnergyMeter());
        assertThat(meter.get("energy_meter_active_power"))
                .extracting(SchemaNode::dataType, SchemaNode::modbusRegisterKind, SchemaNode::modbusAddress)
                .containsExactly(DataType.FLOAT32, "HOLDING_REGISTER", 8);

        Map<String, SchemaNode> plc = nodesById(ModbusTemplates.genericPlcIo());
        assertThat(plc.get("plc_coil_start")).extracting(SchemaNode::modbusRegisterKind, SchemaNode::modbusAddress)
                .containsExactly("COIL", 0);
        assertThat(plc.get("plc_input_running")).extracting(SchemaNode::modbusRegisterKind, SchemaNode::modbusAddress)
                .containsExactly("DISCRETE_INPUT", 0);
        assertThat(plc.get("plc_setpoint")).extracting(SchemaNode::modbusRegisterKind, SchemaNode::modbusAddress)
                .containsExactly("HOLDING_REGISTER", 0);
    }

    @Test
    void sunSpecInverterUsesCommonModelAndModel103Addresses() {
        Map<String, SchemaNode> nodes = nodesById(ModbusTemplates.sunspecInverter());

        assertThat(nodes.get("sunspec_header"))
                .extracting(SchemaNode::dataType, SchemaNode::modbusAddress)
                .containsExactly(DataType.UINT32, 0);
        assertThat(nodes.get("sunspec_common_model_id"))
                .extracting(SchemaNode::dataType, SchemaNode::modbusRegisterKind, SchemaNode::modbusAddress)
                .containsExactly(DataType.UINT16, "HOLDING_REGISTER", 2);
        assertThat(nodes.get("sunspec_common_device_address"))
                .extracting(SchemaNode::modbusAddress).isEqualTo(69);
        assertThat(nodes.get("sunspec_inverter_model_id"))
                .extracting(SchemaNode::dataType, SchemaNode::modbusAddress)
                .containsExactly(DataType.UINT16, 70);
        assertThat(nodes.get("sunspec_inverter_current_phase_c"))
                .extracting(SchemaNode::dataType, SchemaNode::modbusAddress)
                .containsExactly(DataType.INT16, 75);
        assertThat(nodes.get("sunspec_inverter_watt_hours"))
                .extracting(SchemaNode::dataType, SchemaNode::modbusAddress)
                .containsExactly(DataType.UINT32, 95);
        assertThat(nodes.get("sunspec_inverter_events"))
                .extracting(SchemaNode::dataType, SchemaNode::modbusAddress)
                .containsExactly(DataType.UINT32, 111);
    }

    @Test
    void templateVariablesHaveExplicitNonOverlappingHoldingRegisterBindings() {
        List<SchemaNode> variables = ModbusTemplates.sunspecInverter().stream()
                .filter(node -> node.kind() == NodeKind.VARIABLE)
                .toList();
        Set<Integer> occupiedAddresses = new HashSet<>();

        for (SchemaNode node : variables) {
            assertThat(node.modbusRegisterKind()).isEqualTo("HOLDING_REGISTER");
            assertThat(node.modbusAddress()).isNotNull();
            int span = node.dataType() == DataType.UINT32 || node.dataType() == DataType.INT32
                    || node.dataType() == DataType.FLOAT32 ? 2 : 1;
            for (int address = node.modbusAddress(); address < node.modbusAddress() + span; address++) {
                assertThat(occupiedAddresses.add(address))
                        .as("%s must not overlap a previous register binding", node.nodeId())
                        .isTrue();
            }
        }
    }

    private static Map<String, SchemaNode> nodesById(List<SchemaNode> nodes) {
        return nodes.stream().collect(java.util.stream.Collectors.toMap(SchemaNode::nodeId, node -> node));
    }
}
