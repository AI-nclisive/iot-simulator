package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ainclusive.iotsim.api.datasource.DataSourceController;
import com.ainclusive.iotsim.api.datasource.DataSourceController.ConnectionConfigRequest;
import com.ainclusive.iotsim.api.datasource.DataSourceController.CreateDataSourceRequest;
import com.ainclusive.iotsim.api.datasource.DataSourceController.DataSourceResponse;
import com.ainclusive.iotsim.api.error.PreconditionRequiredException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.datasource.CredentialState;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.datasource.Protocol;
import com.ainclusive.iotsim.domain.datasource.RuntimeState;
import com.ainclusive.iotsim.domain.datasource.SourceBasis;
import com.ainclusive.iotsim.platform.secret.ConnectionCredentials;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

/** Unit test for {@link DataSourceController}. */
class DataSourceControllerTest {

    private static final String PROJECT = "proj-1";

    private DataSourceService service;
    private DataSourceController controller;

    @BeforeEach
    void setUp() {
        service = mock(DataSourceService.class);
        controller = new DataSourceController(service);
    }

    private static DataSource sample(long version, RuntimeState state) {
        return sample(version, state, CredentialState.MISSING);
    }

    private static DataSource sample(long version, RuntimeState state, CredentialState credentialState) {
        Instant now = Instant.now();
        return new DataSource("ds1", PROJECT, "Pump", Protocol.OPC_UA, SourceBasis.MANUAL,
                null, null, "{}", "{}", false, state, credentialState, now, now, "local", version);
    }

    @Test
    void createReturns201WithEtag() {
        given(service.create(eq(PROJECT), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(sample(0, RuntimeState.STOPPED));
        ResponseEntity<DataSourceResponse> resp = controller.create(
                PROJECT, new CreateDataSourceRequest("Pump", "OPC_UA", "MANUAL", null, null, null));
        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().protocol()).isEqualTo("OPC_UA");
        assertThat(resp.getBody().runtimeState()).isEqualTo("STOPPED");
        assertThat(resp.getBody().credentialState()).isEqualTo("MISSING");
    }

    @Test
    void createWithBlankNameThrowsBadRequest() {
        assertThatThrownBy(() -> controller.create(
                PROJECT, new CreateDataSourceRequest(" ", "OPC_UA", "MANUAL", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithMissingProtocolThrowsBadRequest() {
        assertThatThrownBy(() -> controller.create(
                PROJECT, new CreateDataSourceRequest("Pump", null, "MANUAL", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createPassesSessionCredentialsToServiceButResponseNeverEchoesTheSecret() {
        given(service.create(eq(PROJECT), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(sample(0, RuntimeState.STOPPED, CredentialState.SESSION_ONLY));
        ConnectionConfigRequest cfg = new ConnectionConfigRequest("password", "operator", "s3cr3t", null);

        ResponseEntity<DataSourceResponse> resp = controller.create(
                PROJECT, new CreateDataSourceRequest("Pump", "OPC_UA", "MANUAL", null, null, cfg));

        ArgumentCaptor<ConnectionCredentials> creds = ArgumentCaptor.forClass(ConnectionCredentials.class);
        verify(service).create(eq(PROJECT), any(), any(), any(), any(), any(), creds.capture(), any());
        assertThat(creds.getValue().mode()).isEqualTo(ConnectionCredentials.Mode.PASSWORD);
        assertThat(creds.getValue().secret()).isEqualTo("s3cr3t");

        // The response carries only credentialState — never the secret (the DTO has no such field).
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().credentialState()).isEqualTo("SESSION_ONLY");
        assertThat(resp.getBody().toString()).doesNotContain("s3cr3t");
    }

    @Test
    void createWithUnknownCredentialModeThrowsBadRequest() {
        ConnectionConfigRequest cfg = new ConnectionConfigRequest("totally-bogus", null, null, null);
        assertThatThrownBy(() -> controller.create(
                PROJECT, new CreateDataSourceRequest("Pump", "OPC_UA", "MANUAL", null, null, cfg)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void connectionConfigRequestRedactsSecretInToString() {
        ConnectionConfigRequest cfg = new ConnectionConfigRequest("password", "operator", "s3cr3t", null);
        assertThat(cfg.toString()).doesNotContain("s3cr3t").contains("***").contains("operator");
    }

    @Test
    void clearCredentialsReturnsMissingState() {
        given(service.clearCredentials(PROJECT, "ds1"))
                .willReturn(sample(0, RuntimeState.STOPPED, CredentialState.MISSING));
        ResponseEntity<DataSourceResponse> resp = controller.clearCredentials(PROJECT, "ds1");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().credentialState()).isEqualTo("MISSING");
    }

    @Test
    void getMissingPropagatesNotFound() {
        given(service.get(PROJECT, "missing")).willThrow(new ResourceNotFoundException("DataSource", "missing"));
        assertThatThrownBy(() -> controller.get(PROJECT, "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateWithoutIfMatchThrowsPreconditionRequired() {
        assertThatThrownBy(() -> controller.update(
                PROJECT, "ds1", null, new DataSourceController.UpdateDataSourceRequest("x", null, null, null, null)))
                .isInstanceOf(PreconditionRequiredException.class);
    }

    @Test
    void startReturnsRunningState() {
        given(service.start(PROJECT, "ds1")).willReturn(sample(0, RuntimeState.RUNNING));
        ResponseEntity<DataSourceResponse> resp = controller.start(PROJECT, "ds1");
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().runtimeState()).isEqualTo("RUNNING");
    }

    @Test
    void deleteReturns204() {
        assertThat(controller.delete(PROJECT, "ds1").getStatusCode().value()).isEqualTo(204);
    }
}
