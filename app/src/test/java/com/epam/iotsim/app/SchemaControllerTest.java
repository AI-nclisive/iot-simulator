package com.epam.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.epam.iotsim.api.schema.SchemaController;
import com.epam.iotsim.api.schema.SchemaController.NodeDto;
import com.epam.iotsim.api.schema.SchemaController.SaveSchemaRequest;
import com.epam.iotsim.api.schema.SchemaController.SchemaResponse;
import com.epam.iotsim.domain.common.ResourceNotFoundException;
import com.epam.iotsim.domain.schema.Schema;
import com.epam.iotsim.domain.schema.SchemaService;
import com.epam.iotsim.protocolmodel.Access;
import com.epam.iotsim.protocolmodel.DataType;
import com.epam.iotsim.protocolmodel.NodeKind;
import com.epam.iotsim.protocolmodel.SchemaNode;
import com.epam.iotsim.protocolmodel.ValueRank;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** Unit test for {@link SchemaController} incl. node validation mapping. */
class SchemaControllerTest {

    private SchemaService service;
    private SchemaController controller;

    @BeforeEach
    void setUp() {
        service = mock(SchemaService.class);
        controller = new SchemaController(service);
    }

    private static Schema sampleSchema(int version) {
        SchemaNode node = new SchemaNode("v1", null, "Plant/Temp", "Temp",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", null);
        return new Schema("s1", "ds1", version, List.of(node), Instant.now());
    }

    @Test
    void getReturnsSchemaWithVersionEtag() {
        given(service.get("p1", "ds1")).willReturn(sampleSchema(3));
        ResponseEntity<SchemaResponse> resp = controller.get("p1", "ds1");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"3\"");
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().nodes()).hasSize(1);
        assertThat(resp.getBody().nodes().get(0).dataType()).isEqualTo("FLOAT64");
    }

    @Test
    void getMissingPropagatesNotFound() {
        given(service.get("p1", "ds1")).willThrow(new ResourceNotFoundException("Schema", "ds1"));
        assertThatThrownBy(() -> controller.get("p1", "ds1"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void saveValidNodesReturnsNewVersion() {
        given(service.save(anyString(), anyString(), any())).willReturn(sampleSchema(1));
        var request = new SaveSchemaRequest(List.of(new NodeDto(
                "v1", null, "Plant/Temp", "Temp", "VARIABLE", "FLOAT64", "SCALAR", "READ", "degC", null)));
        ResponseEntity<SchemaResponse> resp = controller.save("p1", "ds1", request);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"1\"");
    }

    @Test
    void saveVariableWithoutDataTypeIsRejected() {
        var request = new SaveSchemaRequest(List.of(new NodeDto(
                "v1", null, "Plant/Temp", "Temp", "VARIABLE", null, null, null, null, null)));
        assertThatThrownBy(() -> controller.save("p1", "ds1", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveWithInvalidKindIsRejected() {
        var request = new SaveSchemaRequest(List.of(new NodeDto(
                "v1", null, "Plant/Temp", "Temp", "WIDGET", null, null, null, null, null)));
        assertThatThrownBy(() -> controller.save("p1", "ds1", request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void saveWithNullNodesIsRejected() {
        assertThatThrownBy(() -> controller.save("p1", "ds1", new SaveSchemaRequest(null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
