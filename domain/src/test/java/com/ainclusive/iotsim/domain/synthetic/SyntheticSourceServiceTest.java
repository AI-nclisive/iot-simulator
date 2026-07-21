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
import com.ainclusive.iotsim.domain.manualschema.ManualSchema;
import com.ainclusive.iotsim.domain.manualschema.ManualSchemaService;
import com.ainclusive.iotsim.domain.schema.Schema;
import com.ainclusive.iotsim.domain.schema.SchemaService;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
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
    private ManualSchemaService manualSchemas;
    private SyntheticSourceService service;

    @BeforeEach
    void setUp() {
        dataSources = mock(DataSourceService.class);
        schemas = mock(SchemaService.class);
        manualSchemas = mock(ManualSchemaService.class);
        service = new SyntheticSourceService(dataSources, schemas, manualSchemas, new ObjectMapper());
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
                null, null, 0, null, "{}", null, false, RuntimeState.STOPPED, CredentialState.MISSING,
                "opc.tcp://localhost:0/iotsim", now, now, "local", 0);
    }

    @Test
    void createStoresSyntheticBasisAndSerializedConfigAndBuildsSchema() {
        given(dataSources.create(eq(PROJECT), eq("Gen"), eq("OPC_UA"), eq("SYNTHETIC"),
                any(), any(), any(), any(), any(), any(), eq("local"))).willReturn(sample("ds1"));
        given(dataSources.get(PROJECT, "ds1")).willReturn(sample("ds1"));

        DataSource result = service.create(PROJECT, "Gen", "OPC_UA", null, config(), null, null, "local");

        assertThat(result.id()).isEqualTo("ds1");

        ArgumentCaptor<String> runtimeConfig = ArgumentCaptor.forClass(String.class);
        verify(dataSources).create(eq(PROJECT), eq("Gen"), eq("OPC_UA"), eq("SYNTHETIC"),
                any(), eq((String) null), runtimeConfig.capture(), any(), any(), any(), eq("local"));
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
        assertThatThrownBy(() -> service.create(PROJECT, "Gen", "OPC_UA", null, null, null, null, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("config is required");
    }

    @Test
    void createWithInvalidPatternRejectedBeforeAnyWrite() {
        var bad = new SyntheticConfig(1L, List.of(
                new SyntheticVariableConfig("x", DataType.FLOAT64,
                        new PatternSpec("RAMP", null, 0.0, 10.0, null, null, null, null), 100)));
        assertThatThrownBy(() -> service.create(PROJECT, "Gen", "OPC_UA", null, bad, null, null, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("periodMs");
    }

    // ── schemaFromSourceId: reuse an existing source's schema verbatim (IS-145) ──────────────

    private static Schema sourceSchema() {
        // A folder + two variables with real names/paths/units, as a scanned source would have.
        SchemaNode folder = new SchemaNode("f1", null, "/Reactor", "Reactor",
                NodeKind.FOLDER, null, ValueRank.SCALAR, Access.READ, null, null);
        SchemaNode temp = new SchemaNode("temp", "f1", "/Reactor/Temperature", "Temperature",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", "reactor temp");
        SchemaNode level = new SchemaNode("level", "f1", "/Reactor/Level", "Level",
                NodeKind.VARIABLE, DataType.INT32, ValueRank.SCALAR, Access.READ, "%", null);
        return new Schema("sc1", "src1", 2, List.of(folder, temp, level), Instant.now());
    }

    @Test
    void createWithSchemaFromSourceIdCopiesSchemaVerbatim() {
        given(schemas.get(PROJECT, "src1")).willReturn(sourceSchema());
        given(dataSources.create(eq(PROJECT), eq("Twin"), eq("OPC_UA"), eq("SYNTHETIC"),
                any(), any(), any(), any(), any(), any(), eq("local"))).willReturn(sample("ds2"));
        given(dataSources.get(PROJECT, "ds2")).willReturn(sample("ds2"));

        service.create(PROJECT, "Twin", "OPC_UA", null, config(), "src1", null, "local");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SchemaNode>> nodes = ArgumentCaptor.forClass(List.class);
        verify(schemas).save(eq(PROJECT), eq("ds2"), nodes.capture());
        // Full copy: all nodes (folder + variables), original names/paths/units preserved.
        assertThat(nodes.getValue()).hasSize(3);
        SchemaNode temp = nodes.getValue().stream().filter(n -> n.nodeId().equals("temp")).findFirst().orElseThrow();
        assertThat(temp.name()).isEqualTo("Temperature");
        assertThat(temp.path()).isEqualTo("/Reactor/Temperature");
        assertThat(temp.unit()).isEqualTo("degC");
    }

    @Test
    void createWithSchemaFromSourceIdRejectsUnknownNode() {
        given(schemas.get(PROJECT, "src1")).willReturn(sourceSchema());
        var config = new SyntheticConfig(1L, List.of(
                new SyntheticVariableConfig("ghost", DataType.FLOAT64,
                        new PatternSpec("CONSTANT", 1.0, null, null, null, null, null, null), 250)));

        assertThatThrownBy(() -> service.create(PROJECT, "Twin", "OPC_UA", null, config, "src1", null, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void createWithSchemaFromSourceIdRejectsTypeMismatch() {
        given(schemas.get(PROJECT, "src1")).willReturn(sourceSchema());
        var config = new SyntheticConfig(1L, List.of(
                // "temp" is FLOAT64 in the schema; declare it BOOL to force a mismatch.
                new SyntheticVariableConfig("temp", DataType.BOOL,
                        new PatternSpec("CONSTANT", 1.0, null, null, null, null, null, null), 250)));

        assertThatThrownBy(() -> service.create(PROJECT, "Twin", "OPC_UA", null, config, "src1", null, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    // ── manualSchemaId: reuse a standalone manual schema verbatim (IS-173) ───────────────────

    private static ManualSchema sampleManualSchema() {
        SchemaNode folder = new SchemaNode("f1", null, "/Reactor", "Reactor",
                NodeKind.FOLDER, null, ValueRank.SCALAR, Access.READ, null, null);
        SchemaNode temp = new SchemaNode("temp", "f1", "/Reactor/Temperature", "Temperature",
                NodeKind.VARIABLE, DataType.FLOAT64, ValueRank.SCALAR, Access.READ, "degC", "reactor temp");
        SchemaNode level = new SchemaNode("level", "f1", "/Reactor/Level", "Level",
                NodeKind.VARIABLE, DataType.INT32, ValueRank.SCALAR, Access.READ, "%", null);
        Instant now = Instant.now();
        return new ManualSchema("ms1", PROJECT, "OPC_UA", "Reactor template", null,
                List.of(folder, temp, level), now, now, "local", 0);
    }

    @Test
    void createWithManualSchemaIdCopiesSchemaVerbatim() {
        given(manualSchemas.get(PROJECT, "ms1")).willReturn(sampleManualSchema());
        given(dataSources.create(eq(PROJECT), eq("Twin"), eq("OPC_UA"), eq("SYNTHETIC"),
                any(), any(), any(), any(), any(), any(), eq("local"))).willReturn(sample("ds3"));
        given(dataSources.get(PROJECT, "ds3")).willReturn(sample("ds3"));

        service.create(PROJECT, "Twin", "OPC_UA", null, config(), null, "ms1", "local");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<SchemaNode>> nodes = ArgumentCaptor.forClass(List.class);
        verify(schemas).save(eq(PROJECT), eq("ds3"), nodes.capture());
        assertThat(nodes.getValue()).hasSize(3);
        SchemaNode temp = nodes.getValue().stream().filter(n -> n.nodeId().equals("temp")).findFirst().orElseThrow();
        assertThat(temp.name()).isEqualTo("Temperature");
        assertThat(temp.unit()).isEqualTo("degC");
    }

    @Test
    void createWithManualSchemaIdRejectsUnknownNode() {
        given(manualSchemas.get(PROJECT, "ms1")).willReturn(sampleManualSchema());
        var config = new SyntheticConfig(1L, List.of(
                new SyntheticVariableConfig("ghost", DataType.FLOAT64,
                        new PatternSpec("CONSTANT", 1.0, null, null, null, null, null, null), 250)));

        assertThatThrownBy(() -> service.create(PROJECT, "Twin", "OPC_UA", null, config, null, "ms1", "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ghost");
    }

    @Test
    void createWithManualSchemaIdRejectsTypeMismatch() {
        given(manualSchemas.get(PROJECT, "ms1")).willReturn(sampleManualSchema());
        var config = new SyntheticConfig(1L, List.of(
                new SyntheticVariableConfig("temp", DataType.BOOL,
                        new PatternSpec("CONSTANT", 1.0, null, null, null, null, null, null), 250)));

        assertThatThrownBy(() -> service.create(PROJECT, "Twin", "OPC_UA", null, config, null, "ms1", "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not match");
    }

    @Test
    void createWithBothSchemaFromSourceIdAndManualSchemaIdRejected() {
        assertThatThrownBy(() ->
                        service.create(PROJECT, "Twin", "OPC_UA", null, config(), "src1", "ms1", "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mutually exclusive");
    }
}
