package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.ainclusive.iotsim.api.datasource.DataSourceController.DataSourceResponse;
import com.ainclusive.iotsim.api.synthetic.SyntheticSourceController;
import com.ainclusive.iotsim.api.synthetic.SyntheticSourceController.CreateSyntheticSourceRequest;
import com.ainclusive.iotsim.domain.datasource.CredentialState;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.Protocol;
import com.ainclusive.iotsim.domain.datasource.RuntimeState;
import com.ainclusive.iotsim.domain.datasource.SourceBasis;
import com.ainclusive.iotsim.domain.synthetic.PatternSpec;
import com.ainclusive.iotsim.domain.synthetic.SyntheticConfig;
import com.ainclusive.iotsim.domain.synthetic.SyntheticSourceService;
import com.ainclusive.iotsim.domain.synthetic.SyntheticVariableConfig;
import com.ainclusive.iotsim.protocolmodel.DataType;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class SyntheticSourceControllerTest {

    private static final String PROJECT = "p1";

    private SyntheticSourceService service;
    private SyntheticSourceController controller;

    @BeforeEach
    void setUp() {
        service = mock(SyntheticSourceService.class);
        controller = new SyntheticSourceController(service);
    }

    private static SyntheticConfig config() {
        return new SyntheticConfig(1L, List.of(new SyntheticVariableConfig("n", DataType.FLOAT64,
                new PatternSpec("CONSTANT", 1.0, null, null, null, null, null, null), 100)));
    }

    private static DataSource sample() {
        Instant now = Instant.now();
        return new DataSource("ds1", PROJECT, "Gen", Protocol.OPC_UA, SourceBasis.SYNTHETIC,
                "sc1", 1, 4840, null, "{}", false, RuntimeState.STOPPED, CredentialState.MISSING,
                "opc.tcp://localhost:4840/iotsim", now, now, "local", 0);
    }

    @Test
    void createReturns201WithBody() {
        given(service.create(eq(PROJECT), eq("Gen"), eq("OPC_UA"), any(), any(), eq("local")))
                .willReturn(sample());
        ResponseEntity<DataSourceResponse> resp = controller.create(
                PROJECT, new CreateSyntheticSourceRequest("Gen", "OPC_UA", null, config()));
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().basis()).isEqualTo("SYNTHETIC");
    }

    @Test
    void blankNameRejected() {
        assertThatThrownBy(() -> controller.create(
                PROJECT, new CreateSyntheticSourceRequest(" ", "OPC_UA", null, config())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void missingConfigRejected() {
        assertThatThrownBy(() -> controller.create(
                PROJECT, new CreateSyntheticSourceRequest("Gen", "OPC_UA", null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config is required");
    }
}
