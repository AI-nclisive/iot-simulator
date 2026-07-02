package com.ainclusive.iotsim.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.ainclusive.iotsim.api.datasource.DataSourceController;
import com.ainclusive.iotsim.api.datasource.DataSourceController.CreateDataSourceRequest;
import com.ainclusive.iotsim.api.datasource.DataSourceController.DataSourceResponse;
import com.ainclusive.iotsim.api.datasource.DataSourceController.NodeDto;
import com.ainclusive.iotsim.api.error.PreconditionRequiredException;
import com.ainclusive.iotsim.api.support.ConnectionConfigRequest;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.datasource.CredentialState;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.datasource.Protocol;
import com.ainclusive.iotsim.domain.datasource.RuntimeState;
import com.ainclusive.iotsim.domain.datasource.SourceBasis;
import com.ainclusive.iotsim.platform.secret.ConnectionCredentials;
import java.time.Instant;
import java.util.List;
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
                null, null, 4840, null, "{}", false, state, credentialState,
                "opc.tcp://localhost:4840/iotsim", now, now, "local", version);
    }

    @Test
    void createReturns201WithEtag() {
        given(service.create(eq(PROJECT), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(sample(0, RuntimeState.STOPPED));
        ResponseEntity<DataSourceResponse> resp = controller.create(
                PROJECT, new CreateDataSourceRequest("Pump", "OPC_UA", "MANUAL", null, null, null, null, null));
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
                PROJECT, new CreateDataSourceRequest(" ", "OPC_UA", "MANUAL", null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithMissingProtocolThrowsBadRequest() {
        assertThatThrownBy(() -> controller.create(
                PROJECT, new CreateDataSourceRequest("Pump", null, "MANUAL", null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createPassesSessionCredentialsToServiceButResponseNeverEchoesTheSecret() {
        given(service.create(eq(PROJECT), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(sample(0, RuntimeState.STOPPED, CredentialState.SESSION_ONLY));
        ConnectionConfigRequest cfg = new ConnectionConfigRequest("password", "operator", "s3cr3t", null);

        ResponseEntity<DataSourceResponse> resp = controller.create(
                PROJECT, new CreateDataSourceRequest("Pump", "OPC_UA", "MANUAL", null, null, null, cfg, null));

        ArgumentCaptor<ConnectionCredentials> creds = ArgumentCaptor.forClass(ConnectionCredentials.class);
        verify(service).create(eq(PROJECT), any(), any(), any(), any(), any(), any(), creds.capture(), any(), any());
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
                PROJECT, new CreateDataSourceRequest("Pump", "OPC_UA", "MANUAL", null, null, null, cfg, null)))
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
                PROJECT, "ds1", null, new DataSourceController.UpdateDataSourceRequest("x", null, null, null, null, null)))
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

    @Test
    void createWithMissingBasisThrowsBadRequest() {
        assertThatThrownBy(() -> controller.create(
                PROJECT, new CreateDataSourceRequest("Pump", "OPC_UA", null, null, null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createMapsHyphenatedExternalRefModeToExternalRefCredentials() {
        given(service.create(eq(PROJECT), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(sample(0, RuntimeState.STOPPED, CredentialState.SESSION_ONLY));
        // The UI sends "external-ref" (hyphen); the controller normalizes it to EXTERNAL_REF.
        ConnectionConfigRequest cfg = new ConnectionConfigRequest("external-ref", null, null, "vault://pump");

        controller.create(PROJECT, new CreateDataSourceRequest("Pump", "OPC_UA", "MANUAL", null, null, null, cfg, null));

        ArgumentCaptor<ConnectionCredentials> creds = ArgumentCaptor.forClass(ConnectionCredentials.class);
        verify(service).create(eq(PROJECT), any(), any(), any(), any(), any(), any(), creds.capture(), any(), any());
        assertThat(creds.getValue().mode()).isEqualTo(ConnectionCredentials.Mode.EXTERNAL_REF);
        assertThat(creds.getValue().secretRef()).isEqualTo("vault://pump");
    }

    @Test
    void createMapsBlankAndAnonymousModesToAnonymousCredentialsCaseInsensitively() {
        given(service.create(eq(PROJECT), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(sample(0, RuntimeState.STOPPED));

        // Empty mode and mixed-case "Anonymous" both resolve to anonymous credentials
        // ("Anonymous" only matches the lowercase switch case via the controller's toLowerCase).
        controller.create(PROJECT, new CreateDataSourceRequest(
                "Pump", "OPC_UA", "MANUAL", null, null, null, new ConnectionConfigRequest("", null, null, null), null));
        controller.create(PROJECT, new CreateDataSourceRequest(
                "Pump", "OPC_UA", "MANUAL", null, null, null,
                new ConnectionConfigRequest("Anonymous", null, null, null), null));

        ArgumentCaptor<ConnectionCredentials> creds = ArgumentCaptor.forClass(ConnectionCredentials.class);
        verify(service, times(2)).create(eq(PROJECT), any(), any(), any(), any(), any(), any(), creds.capture(), any(), any());
        assertThat(creds.getAllValues()).allSatisfy(c ->
                assertThat(c.mode()).isEqualTo(ConnectionCredentials.Mode.ANONYMOUS));
    }

    @Test
    void createMapsMixedCasePasswordMode() {
        given(service.create(eq(PROJECT), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(sample(0, RuntimeState.STOPPED, CredentialState.SESSION_ONLY));
        ConnectionConfigRequest cfg = new ConnectionConfigRequest("Password", "operator", "s3cr3t", null);

        controller.create(PROJECT, new CreateDataSourceRequest("Pump", "OPC_UA", "MANUAL", null, null, null, cfg, null));

        ArgumentCaptor<ConnectionCredentials> creds = ArgumentCaptor.forClass(ConnectionCredentials.class);
        verify(service).create(eq(PROJECT), any(), any(), any(), any(), any(), any(), creds.capture(), any(), any());
        assertThat(creds.getValue().mode()).isEqualTo(ConnectionCredentials.Mode.PASSWORD);
        assertThat(creds.getValue().secret()).isEqualTo("s3cr3t");
    }

    @Test
    void updatePassesCredentialsToServiceButResponseNeverEchoesTheSecret() {
        given(service.update(eq(PROJECT), eq("ds1"), any(), any(), any(), any(), any(), any(), eq(3L)))
                .willReturn(sample(4, RuntimeState.STOPPED, CredentialState.SESSION_ONLY));
        ConnectionConfigRequest cfg = new ConnectionConfigRequest("password", "operator", "s3cr3t", null);

        ResponseEntity<DataSourceResponse> resp = controller.update(PROJECT, "ds1", "\"3\"",
                new DataSourceController.UpdateDataSourceRequest(null, null, null, null, null, cfg));

        ArgumentCaptor<ConnectionCredentials> creds = ArgumentCaptor.forClass(ConnectionCredentials.class);
        verify(service).update(eq(PROJECT), eq("ds1"), any(), any(), any(), any(), any(), creds.capture(), eq(3L));
        assertThat(creds.getValue().secret()).isEqualTo("s3cr3t");
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().credentialState()).isEqualTo("SESSION_ONLY");
        assertThat(resp.getBody().toString()).doesNotContain("s3cr3t");
    }

    @Test
    void duplicateReturns201WithNewIdAndCopyName() {
        Instant now = Instant.now();
        DataSource copy = new DataSource("ds2", PROJECT, "Pump (copy)", Protocol.OPC_UA, SourceBasis.MANUAL,
                null, null, 4840, null, "{}", false, RuntimeState.STOPPED, CredentialState.MISSING,
                "opc.tcp://localhost:4840/iotsim", now, now, "local", 0);
        given(service.duplicate(PROJECT, "ds1", "local")).willReturn(copy);

        ResponseEntity<DataSourceResponse> resp = controller.duplicate(PROJECT, "ds1");

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getHeaders().getLocation()).isNotNull();
        assertThat(resp.getHeaders().getLocation().toString()).contains("/data-sources/ds2");
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().id()).isEqualTo("ds2");
        assertThat(resp.getBody().name()).isEqualTo("Pump (copy)");
        assertThat(resp.getBody().enabled()).isFalse();
        assertThat(resp.getBody().runtimeState()).isEqualTo("STOPPED");
    }

    @Test
    void duplicatePropagatesNotFoundWhenSourceMissing() {
        given(service.duplicate(PROJECT, "missing", "local"))
                .willThrow(new ResourceNotFoundException("DataSource", "missing"));
        assertThatThrownBy(() -> controller.duplicate(PROJECT, "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createWithInitialSchemaPassesNodesToService() {
        Instant now = Instant.now();
        DataSource imported = new DataSource("ds1", PROJECT, "Sensor", Protocol.OPC_UA, SourceBasis.IMPORT,
                "schema-1", 1, 4840, null, "{}", false, RuntimeState.STOPPED, CredentialState.MISSING,
                "opc.tcp://localhost:4840/iotsim", now, now, "local", 0);
        given(service.create(eq(PROJECT), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .willReturn(imported);

        NodeDto node = new NodeDto("n1", null, "/root/temp", "Temperature", "VARIABLE",
                "FLOAT32", "SCALAR", "READ", "°C", null);
        ResponseEntity<DataSourceResponse> resp = controller.create(
                PROJECT, new CreateDataSourceRequest("Sensor", "OPC_UA", "IMPORT", null, null, null, null, List.of(node)));

        assertThat(resp.getStatusCode().value()).isEqualTo(201);
        assertThat(resp.getBody()).isNotNull();
        assertThat(resp.getBody().schemaVersion()).isEqualTo(1);
        assertThat(resp.getBody().basis()).isEqualTo("IMPORT");
    }

    @Test
    void createWithBlankNodeIdInInitialSchemaIsRejected() {
        NodeDto bad = new NodeDto("", null, "/root/temp", "Temperature", "VARIABLE",
                "FLOAT32", "SCALAR", "READ", null, null);
        assertThatThrownBy(() -> controller.create(
                PROJECT, new CreateDataSourceRequest("Sensor", "OPC_UA", "IMPORT", null, null, null, null, List.of(bad))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nodeId");
    }

    @Test
    void createWithBlankNameInInitialSchemaIsRejected() {
        NodeDto bad = new NodeDto("n1", null, "/root/temp", "  ", "VARIABLE",
                "FLOAT32", "SCALAR", "READ", null, null);
        assertThatThrownBy(() -> controller.create(
                PROJECT, new CreateDataSourceRequest("Sensor", "OPC_UA", "IMPORT", null, null, null, null, List.of(bad))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    void createWithBlankPathInInitialSchemaIsRejected() {
        NodeDto bad = new NodeDto("n1", null, "  ", "Temperature", "VARIABLE",
                "FLOAT32", "SCALAR", "READ", null, null);
        assertThatThrownBy(() -> controller.create(
                PROJECT, new CreateDataSourceRequest("Sensor", "OPC_UA", "IMPORT", null, null, null, null, List.of(bad))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path");
    }

    @Test
    void createWithUnknownNodeKindInInitialSchemaIsRejected() {
        NodeDto bad = new NodeDto("n1", null, "/root/temp", "Temperature", "BOGUS_KIND",
                null, null, null, null, null);
        assertThatThrownBy(() -> controller.create(
                PROJECT, new CreateDataSourceRequest("Sensor", "OPC_UA", "IMPORT", null, null, null, null, List.of(bad))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kind");
    }

    @Test
    void createWithVariableNodeMissingDataTypeIsRejected() {
        NodeDto bad = new NodeDto("n1", null, "/root/temp", "Temperature", "VARIABLE",
                null, "SCALAR", "READ", null, null);
        assertThatThrownBy(() -> controller.create(
                PROJECT, new CreateDataSourceRequest("Sensor", "OPC_UA", "IMPORT", null, null, null, null, List.of(bad))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataType");
    }
}
