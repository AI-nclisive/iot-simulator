package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ainclusive.iotsim.api.error.PreconditionRequiredException;
import com.ainclusive.iotsim.api.manualschema.ManualSchemaController;
import com.ainclusive.iotsim.api.manualschema.ManualSchemaController.CreateManualSchemaRequest;
import com.ainclusive.iotsim.api.manualschema.ManualSchemaController.DuplicateManualSchemaRequest;
import com.ainclusive.iotsim.api.manualschema.ManualSchemaController.ManualSchemaResponse;
import com.ainclusive.iotsim.api.manualschema.ManualSchemaController.NodeDto;
import com.ainclusive.iotsim.api.manualschema.ManualSchemaController.UpdateManualSchemaRequest;
import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.manualschema.ManualSchema;
import com.ainclusive.iotsim.domain.manualschema.ManualSchemaService;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
        given(service.list("p1")).willReturn(List.of(sample(0)));
        List<ManualSchemaResponse> resp = controller.list("p1");
        assertThat(resp).hasSize(1);
        assertThat(resp.get(0).name()).isEqualTo("Boiler");
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
}
