package com.ainclusive.iotsim.domain.synthetic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ainclusive.iotsim.domain.datasource.CredentialState;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.datasource.Protocol;
import com.ainclusive.iotsim.domain.datasource.RuntimeState;
import com.ainclusive.iotsim.domain.datasource.SourceBasis;
import com.ainclusive.iotsim.domain.schema.SchemaService;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

class SyntheticSourceServiceTest {

    private static final String PROJECT = "p1";

    private DataSourceService dataSources;
    private SchemaService schemas;
    private SyntheticSourceService service;

    @BeforeEach
    void setUp() {
        dataSources = mock(DataSourceService.class);
        schemas = mock(SchemaService.class);
        service = new SyntheticSourceService(dataSources, schemas, new ObjectMapper());
    }

    private static SyntheticConfig config() {
        return new SyntheticConfig(9L, List.of(
                new SyntheticVariableConfig("temp", DataType.FLOAT64,
                        new PatternSpec("SINE", null, 0.0, 10.0, 1000L, null, null, null), 250),
                new SyntheticVariableConfig("level", DataType.INT32,
                        new PatternSpec("CONSTANT", 3.0, null, null, null, null, null, null), 500)));
    }

    private static DataSource sample(String id) {
        Instant now = Instant.now();
        return new DataSource(id, PROJECT, "Gen", Protocol.OPC_UA, SourceBasis.SYNTHETIC,
                null, null, "{}", "{}", false, RuntimeState.STOPPED, CredentialState.MISSING,
                now, now, "local", 0);
    }

    @Test
    void createStoresSyntheticBasisAndSerializedConfigAndBuildsSchema() {
        given(dataSources.create(eq(PROJECT), eq("Gen"), eq("OPC_UA"), eq("SYNTHETIC"),
                any(), any(), any(), any(), eq("local"))).willReturn(sample("ds1"));
        given(dataSources.get(PROJECT, "ds1")).willReturn(sample("ds1"));

        DataSource result = service.create(PROJECT, "Gen", "OPC_UA", "{}", config(), "local");

        assertThat(result.id()).isEqualTo("ds1");

        ArgumentCaptor<String> runtimeConfig = ArgumentCaptor.forClass(String.class);
        verify(dataSources).create(eq(PROJECT), eq("Gen"), eq("OPC_UA"), eq("SYNTHETIC"),
                eq("{}"), runtimeConfig.capture(), any(), any(), eq("local"));
        assertThat(runtimeConfig.getValue()).contains("\"seed\":9").contains("temp").contains("level");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SchemaNode>> nodes = ArgumentCaptor.forClass(List.class);
        verify(schemas).save(eq(PROJECT), eq("ds1"), nodes.capture());
        assertThat(nodes.getValue()).hasSize(2);
        SchemaNode first = nodes.getValue().get(0);
        assertThat(first.nodeId()).isEqualTo("temp");
        assertThat(first.kind()).isEqualTo(NodeKind.VARIABLE);
        assertThat(first.dataType()).isEqualTo(DataType.FLOAT64);
    }

    @Test
    void createWithNullConfigRejected() {
        assertThatThrownBy(() -> service.create(PROJECT, "Gen", "OPC_UA", "{}", null, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config is required");
    }

    @Test
    void createWithInvalidPatternRejectedBeforeAnyWrite() {
        var bad = new SyntheticConfig(1L, List.of(
                new SyntheticVariableConfig("x", DataType.FLOAT64,
                        new PatternSpec("RAMP", null, 0.0, 10.0, null, null, null, null), 100)));
        assertThatThrownBy(() -> service.create(PROJECT, "Gen", "OPC_UA", "{}", bad, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodMs");
    }
}
