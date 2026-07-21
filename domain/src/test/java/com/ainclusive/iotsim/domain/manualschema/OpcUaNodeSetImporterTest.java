package com.ainclusive.iotsim.domain.manualschema;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.ReferenceType;
import org.junit.jupiter.api.Test;

class OpcUaNodeSetImporterTest {
    @Test
    void importsObjectsVariablesMethodsArraysAndReferencesWithoutFlattening() {
        var result = OpcUaNodeSetImporter.importXml("""
                <UANodeSet xmlns="http://opcfoundation.org/UA/2011/03/UANodeSet.xsd">
                  <UAObject NodeId="ns=2;s=Plant" BrowseName="2:Plant"><References>
                    <Reference ReferenceType="Organizes">ns=2;s=Tank</Reference></References></UAObject>
                  <UAObject NodeId="ns=2;s=Tank" BrowseName="2:Tank"><References>
                    <Reference ReferenceType="Organizes" IsForward="false">ns=2;s=Plant</Reference>
                    <Reference ReferenceType="HasComponent">ns=2;s=Temperature</Reference>
                    <Reference ReferenceType="HasComponent">ns=2;s=Reset</Reference></References></UAObject>
                  <UAVariable NodeId="ns=2;s=Temperature" BrowseName="2:Temperature" DataType="i=11" ValueRank="1" AccessLevel="3"><References>
                    <Reference ReferenceType="HasComponent" IsForward="false">ns=2;s=Tank</Reference></References></UAVariable>
                  <UAMethod NodeId="ns=2;s=Reset" BrowseName="2:Reset"><References>
                    <Reference ReferenceType="HasComponent" IsForward="false">ns=2;s=Tank</Reference></References></UAMethod>
                </UANodeSet>""");

        assertThat(result.diagnostics()).isEmpty();
        assertThat(result.nodes()).hasSize(4);
        assertThat(result.nodes()).anySatisfy(node -> {
            assertThat(node.name()).isEqualTo("Temperature");
            assertThat(node.parentId()).isEqualTo("ns=2;s=Tank");
            assertThat(node.path()).isEqualTo("Plant/Tank/Temperature");
            assertThat(node.dataType()).isEqualTo(DataType.FLOAT64);
            assertThat(node.arrayDimensions()).isEmpty();
        });
        assertThat(result.nodes()).anySatisfy(node -> {
            assertThat(node.name()).isEqualTo("Reset");
            assertThat(node.kind()).isEqualTo(NodeKind.METHOD);
            assertThat(node.parentId()).isEqualTo("ns=2;s=Tank");
        });
        assertThat(result.nodes().getFirst().references()).extracting(reference -> reference.type())
                .contains(ReferenceType.ORGANIZES);
    }

    @Test
    void reportsUnsupportedNodesAndVariablesInsteadOfDroppingThemSilently() {
        var result = OpcUaNodeSetImporter.importXml("""
                <UANodeSet xmlns="http://opcfoundation.org/UA/2011/03/UANodeSet.xsd">
                  <UAObjectType NodeId="ns=2;s=PumpType" BrowseName="2:PumpType" />
                  <UAVariable NodeId="ns=2;s=Custom" BrowseName="2:Custom" DataType="ns=2;i=3001" />
                </UANodeSet>""");

        assertThat(result.nodes()).isEmpty();
        assertThat(result.diagnostics()).extracting(OpcUaNodeSetImporter.Diagnostic::severity)
                .containsOnly("UNSUPPORTED");
        assertThat(result.diagnostics()).extracting(OpcUaNodeSetImporter.Diagnostic::subject)
                .containsExactlyInAnyOrder("node", "variable");
    }

    @Test
    void reportsAndSkipsCyclicHierarchyInsteadOfFailingTheWholeImport() {
        var result = OpcUaNodeSetImporter.importXml("""
                <UANodeSet xmlns="http://opcfoundation.org/UA/2011/03/UANodeSet.xsd">
                  <UAObject NodeId="ns=2;s=A" BrowseName="2:A"><References>
                    <Reference ReferenceType="Organizes">ns=2;s=B</Reference></References></UAObject>
                  <UAObject NodeId="ns=2;s=B" BrowseName="2:B"><References>
                    <Reference ReferenceType="Organizes">ns=2;s=A</Reference>
                    <Reference ReferenceType="HasComponent">ns=2;s=C</Reference></References></UAObject>
                  <UAVariable NodeId="ns=2;s=C" BrowseName="2:C" DataType="i=11"><References>
                    <Reference ReferenceType="HasComponent" IsForward="false">ns=2;s=B</Reference></References></UAVariable>
                </UANodeSet>""");

        assertThat(result.nodes()).isEmpty();
        assertThat(result.diagnostics()).extracting(OpcUaNodeSetImporter.Diagnostic::message)
                .allMatch(message -> message.contains("Cyclic hierarchical references"));
    }
}
