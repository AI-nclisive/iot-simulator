package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ainclusive.iotsim.api.error.PreconditionRequiredException;
import com.ainclusive.iotsim.api.manualschema.ManualSchemaController;
import com.ainclusive.iotsim.api.manualschema.ManualSchemaController.CreateManualSchemaRequest;
import com.ainclusive.iotsim.api.manualschema.ManualSchemaController.DuplicateManualSchemaRequest;
import com.ainclusive.iotsim.api.manualschema.ManualSchemaController.ManualSchemaResponse;
import com.ainclusive.iotsim.api.manualschema.ManualSchemaController.NodeDto;
import com.ainclusive.iotsim.api.manualschema.ManualSchemaController.UpdateManualSchemaRequest;
import com.ainclusive.iotsim.api.schema.MemberDto;
import com.ainclusive.iotsim.api.schema.ReferenceDto;
import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.manualschema.ManualSchema;
import com.ainclusive.iotsim.domain.manualschema.ManualSchemaService;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.ReferenceType;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

/** Unit test for {@link ManualSchemaController} incl. node validation and ETag/If-Match mapping. */
class ManualSchemaControllerTest {

    private ManualSchemaService service;
    private ManualSchemaController controller;

    @BeforeEach
    void setUp() {
        service = mock(ManualSchemaService.class);
        controller = new ManualSchemaController(service);
    }

    private static ManualSchema sample(long version) {
        SchemaNode node = new SchemaNode("v1", null, "Plant/Temp", "Temp",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", null);
        return new ManualSchema("ms1", "p1", "OPC_UA", "Boiler", "desc", List.of(node),
                Instant.now(), Instant.now(), "local", version);
    }

    @Test
    void listReturnsSchemas() {
        given(service.listPaged("p1", null, null)).willReturn(new Page<>(List.of(sample(0)), null, 50));
        Page<ManualSchemaResponse> resp = controller.list("p1", null, null);
        assertThat(resp.items()).hasSize(1);
        assertThat(resp.items().get(0).name()).isEqualTo("Boiler");
        assertThat(resp.nextCursor()).isNull();
    }

    @Test
    void createValidNodesReturnsCreatedWithEtag() {
        given(service.create(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .willReturn(sample(0));
        var request = new CreateManualSchemaRequest("OPC_UA", "Boiler", "desc", List.of(new NodeDto(
                "v1", null, "Plant/Temp", "Temp", "VARIABLE", "FLOAT64", "SCALAR", "READ", "degC", null)));
        ResponseEntity<ManualSchemaResponse> resp = controller.create("p1", request);
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(resp.getHeaders().getLocation()).hasToString("/api/v1/projects/p1/manual-schemas/ms1");
    }

    @Test
    void createWithoutNameIsRejected() {
        var request = new CreateManualSchemaRequest("OPC_UA", " ", null, List.of());
        assertThatThrownBy(() -> controller.create("p1", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createVariableWithoutDataTypeIsRejected() {
        var request = new CreateManualSchemaRequest("OPC_UA", "Boiler", null, List.of(new NodeDto(
                "v1", null, "Plant/Temp", "Temp", "VARIABLE", null, null, null, null, null)));
        assertThatThrownBy(() -> controller.create("p1", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getReturnsSchemaWithVersionEtag() {
        given(service.get("p1", "ms1")).willReturn(sample(3));
        ResponseEntity<ManualSchemaResponse> resp = controller.get("p1", "ms1");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"3\"");
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().nodes().get(0).dataType()).isEqualTo("FLOAT64");
    }

    @Test
    void getMissingPropagatesNotFound() {
        given(service.get("p1", "ms1")).willThrow(new ResourceNotFoundException("ManualSchema", "ms1"));
        assertThatThrownBy(() -> controller.get("p1", "ms1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateWithoutIfMatchIsRejected() {
        var request = new UpdateManualSchemaRequest("Boiler v2", null, List.of());
        assertThatThrownBy(() -> controller.update("p1", "ms1", null, request))
                .isInstanceOf(PreconditionRequiredException.class);
    }

    @Test
    void updateWithIfMatchSavesInPlace() {
        given(service.update(anyString(), anyString(), anyString(), any(), any(), anyLong()))
                .willReturn(sample(1));
        var request = new UpdateManualSchemaRequest("Boiler v2", "d2", List.of());
        ResponseEntity<ManualSchemaResponse> resp = controller.update("p1", "ms1", "\"0\"", request);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"1\"");
    }

    @Test
    void updateWithStaleVersionPropagatesConflict() {
        given(service.update(anyString(), anyString(), anyString(), any(), any(), anyLong()))
                .willThrow(new ConcurrencyConflictException("ManualSchema", "ms1", 0));
        var request = new UpdateManualSchemaRequest("x", null, List.of());
        assertThatThrownBy(() -> controller.update("p1", "ms1", "\"0\"", request))
                .isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void duplicateReturnsCreatedWithNewLocation() {
        ManualSchema copy = new ManualSchema("ms2", "p1", "OPC_UA", "Boiler (copy)", "desc", List.of(),
                Instant.now(), Instant.now(), "local", 0);
        given(service.duplicate("p1", "ms1", "Boiler (copy)", "local")).willReturn(copy);
        ResponseEntity<ManualSchemaResponse> resp =
                controller.duplicate("p1", "ms1", new DuplicateManualSchemaRequest("Boiler (copy)"));
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getHeaders().getLocation()).hasToString("/api/v1/projects/p1/manual-schemas/ms2");
    }

    @Test
    void duplicateWithoutNameIsRejected() {
        assertThatThrownBy(() -> controller.duplicate("p1", "ms1", new DuplicateManualSchemaRequest(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteReturnsNoContent() {
        ResponseEntity<Void> resp = controller.delete("p1", "ms1");
        assertThat(resp.getStatusCode().value()).isEqualTo(204);
    }

    @Test
    void createRoundTripsDataTypeNodeAndVariableReferencingIt() {
        given(service.create(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .willReturn(sample(0));
        var dataTypeDto = new NodeDto("dt1", null, "Vector3D", "Vector3D", "DATA_TYPE",
                null, null, null, null, null, List.of(), null, List.of(), null,
                List.of(new MemberDto("x", "FLOAT64", null), new MemberDto("y", "FLOAT64", null)),
                null, null, null, null);
        var variableDto = new NodeDto("v1", null, "Plant/Vec", "Vec", "VARIABLE",
                null, "SCALAR", "READ", null, null, List.of(), null, List.of(), "dt1", List.of(),
                null, null, null, null);
        var request = new CreateManualSchemaRequest("OPC_UA", "Boiler", "desc",
                List.of(dataTypeDto, variableDto));

        controller.create("p1", request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SchemaNode>> nodesCaptor = ArgumentCaptor.forClass(List.class);
        verify(service).create(anyString(), anyString(), anyString(), any(), nodesCaptor.capture(), anyString());
        List<SchemaNode> savedNodes = nodesCaptor.getValue();
        SchemaNode savedDataType = savedNodes.stream().filter(n -> n.nodeId().equals("dt1")).findFirst().orElseThrow();
        SchemaNode savedVariable = savedNodes.stream().filter(n -> n.nodeId().equals("v1")).findFirst().orElseThrow();
        assertThat(savedDataType.kind()).isEqualTo(NodeKind.DATA_TYPE);
        assertThat(savedDataType.members()).extracting("name").containsExactly("x", "y");
        assertThat(savedVariable.dataType()).isNull();
        assertThat(savedVariable.dataTypeNodeId()).isEqualTo("dt1");
    }

    @Test
    void createRejectsVariableWithBothDataTypeAndDataTypeNodeId() {
        var variableDto = new NodeDto("v1", null, "Plant/Vec", "Vec", "VARIABLE",
                "FLOAT64", "SCALAR", "READ", null, null, List.of(), null, List.of(), "dt1", List.of(),
                null, null, null, null);
        var request = new CreateManualSchemaRequest("OPC_UA", "Boiler", "desc", List.of(variableDto));

        assertThatThrownBy(() -> controller.create("p1", request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one");
    }

    @Test
    void createVariableWithPropertyChild() {
        given(service.create(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .willReturn(sample(0));
        var parentVar = new NodeDto("v1", null, "Plant/Temp", "Temp", "VARIABLE",
                "FLOAT64", "SCALAR", "READ", "degC", null, List.of(), null, List.of(), null, List.of(),
                null, null, null, null);
        var childProp = new NodeDto("p1", "v1", "Plant/Temp/Status", "Status", "VARIABLE",
                "BOOL", "SCALAR", "READ", null, null, List.of(), null,
                List.of(new ReferenceDto("v1", "HAS_PROPERTY", false)), null, List.of(),
                null, null, null, null);
        var request = new CreateManualSchemaRequest("OPC_UA", "Boiler", "desc",
                List.of(parentVar, childProp));

        controller.create("p1", request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SchemaNode>> nodesCaptor = ArgumentCaptor.forClass(List.class);
        verify(service).create(anyString(), anyString(), anyString(), any(), nodesCaptor.capture(), anyString());
        List<SchemaNode> savedNodes = nodesCaptor.getValue();
        SchemaNode savedParent = savedNodes.stream().filter(n -> n.nodeId().equals("v1")).findFirst().orElseThrow();
        SchemaNode savedChild = savedNodes.stream().filter(n -> n.nodeId().equals("p1")).findFirst().orElseThrow();
        assertThat(savedParent.kind()).isEqualTo(NodeKind.VARIABLE);
        assertThat(savedParent.parentId()).isNull();
        assertThat(savedChild.kind()).isEqualTo(NodeKind.VARIABLE);
        assertThat(savedChild.parentId()).isEqualTo("v1");
        assertThat(savedChild.references()).hasSize(1);
        assertThat(savedChild.references().get(0).type()).isEqualTo(ReferenceType.HAS_PROPERTY);
    }

    @Test
    void createVariableWithOpcUaAttributesPersists() {
        given(service.create(anyString(), anyString(), anyString(), any(), any(), anyString()))
                .willReturn(sample(0));
        var variableDto = new NodeDto("v1", null, "Plant/Temp", "Temp", "VARIABLE",
                "FLOAT64", "SCALAR", "READ", "degC", null, List.of(), null, List.of(), null, List.of(),
                0x03, 100, 0xFF, true);
        var request = new CreateManualSchemaRequest("OPC_UA", "Boiler", "desc", List.of(variableDto));

        controller.create("p1", request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SchemaNode>> nodesCaptor = ArgumentCaptor.forClass(List.class);
        verify(service).create(anyString(), anyString(), anyString(), any(), nodesCaptor.capture(), anyString());
        List<SchemaNode> savedNodes = nodesCaptor.getValue();
        SchemaNode savedNode = savedNodes.stream().filter(n -> n.nodeId().equals("v1")).findFirst().orElseThrow();
        assertThat(savedNode.accessLevelFull()).isEqualTo(0x03);
        assertThat(savedNode.minimumSamplingInterval()).isEqualTo(100);
        assertThat(savedNode.writeMask()).isEqualTo(0xFF);
        assertThat(savedNode.historizing()).isTrue();
    }
}
