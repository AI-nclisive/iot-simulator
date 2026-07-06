package com.ainclusive.iotsim.domain.io;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.domain.datasource.CredentialState;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.Protocol;
import com.ainclusive.iotsim.domain.datasource.RuntimeState;
import com.ainclusive.iotsim.domain.datasource.SourceBasis;
import com.ainclusive.iotsim.domain.project.Project;
import com.ainclusive.iotsim.domain.recording.Recording;
import com.ainclusive.iotsim.domain.sample.Sample;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProjectZipExporterTest {

    private final ProjectZipExporter exporter = new ProjectZipExporter(new ObjectMapper());

    @Test
    void writesAllRequiredZipEntries() {
        Map<String, String> zip = unzip(exporter.toBytes(fullContent()));

        assertThat(zip).containsKeys(
                "manifest.json",
                "project.json",
                "data-sources.json",
                "schemas.json",
                "recordings.json",
                "samples.json");
    }

    @Test
    void manifestContainsFormatVersionAndProjectInfo() {
        String manifestJson = unzip(exporter.toBytes(fullContent())).get("manifest.json");

        assertThat(manifestJson)
                .contains("\"formatVersion\":\"1.0.0\"")
                .contains("\"id\":\"proj-1\"")
                .contains("\"name\":\"Test Project\"");
    }

    @Test
    void manifestContainsChecksumEntries() {
        String manifestJson = unzip(exporter.toBytes(fullContent())).get("manifest.json");

        assertThat(manifestJson)
                .contains("\"path\":\"project.json\"")
                .contains("\"sha256\":");
    }

    @Test
    void projectJsonContainsMetadataWithoutSecrets() {
        String projectJson = unzip(exporter.toBytes(fullContent())).get("project.json");

        assertThat(projectJson)
                .contains("\"id\":\"proj-1\"")
                .contains("\"name\":\"Test Project\"")
                .contains("\"status\":\"ACTIVE\"");
    }

    @Test
    void dataSourceJsonExcludesCredentialAndRuntimeState() {
        String dsJson = unzip(exporter.toBytes(fullContent())).get("data-sources.json");

        assertThat(dsJson)
                .contains("\"id\":\"ds-1\"")
                .contains("\"protocol\":\"OPC_UA\"")
                .contains("\"simulatorPort\":4840")
                .contains("\"realDeviceEndpoint\":\"device://plc\"")
                .doesNotContain("credentialState")
                .doesNotContain("runtimeState");
    }

    @Test
    void dataSourceJsonIncludesHashedSecurityConfig() {
        // Build content with a hashed securityConfig and verify it round-trips through export.
        // The securityConfig JSON must surface the PBKDF2 hash but never expose a plaintext
        // "password" key (IS-131 contract: hashes exported, plaintext credentials excluded).
        String storedHash = "{\"userTokens\":{\"anonymous\":false,\"username\":{\"enabled\":true,"
                + "\"users\":[{\"username\":\"op\",\"passwordHash\":\"pbkdf2-sha256$1$aa$bb\"}]}}}";
        ProjectExportContent content = contentWithSecurityConfig(storedHash);

        String dsJson = unzip(exporter.toBytes(content)).get("data-sources.json");

        assertThat(dsJson)
                .contains("securityConfig")
                .contains("pbkdf2-sha256$1$aa$bb")
                // plaintext "password" JSON key must never appear — only passwordHash is allowed
                .doesNotContain("\"password\"");
    }

    @Test
    void schemasJsonContainsNodes() {
        String schemasJson = unzip(exporter.toBytes(fullContent())).get("schemas.json");

        assertThat(schemasJson)
                .contains("\"dataSourceId\":\"ds-1\"")
                .contains("\"ns=2;s=Temp\"");
    }

    @Test
    void recordingsJsonContainsMetadata() {
        String recordingsJson = unzip(exporter.toBytes(fullContent())).get("recordings.json");

        assertThat(recordingsJson)
                .contains("\"id\":\"rec-1\"")
                .contains("\"dataSourceId\":\"ds-1\"");
    }

    @Test
    void samplesJsonContainsMetadata() {
        String samplesJson = unzip(exporter.toBytes(fullContent())).get("samples.json");

        assertThat(samplesJson)
                .contains("\"id\":\"smp-1\"")
                .contains("\"name\":\"Sample A\"");
    }

    @Test
    void neverSerializesSecretLikeFields() {
        String all = String.join("\n", unzip(exporter.toBytes(fullContent())).values());

        // "password" (quoted JSON key) must never appear as a plaintext field.
        // The intentional "passwordHash" key is allowed; when lowercased, "passwordhash"
        // does NOT contain the quoted substring "\"password\"", so this assertion still
        // catches any accidental plaintext password field while permitting the hash.
        assertThat(all.toLowerCase())
                .doesNotContain("\"password\"")
                .doesNotContain("credential")
                .doesNotContain("privatekey")
                .doesNotContain("secret");
    }

    @Test
    void exportRoundtripsToNonEmptyZip() {
        byte[] bytes = exporter.toBytes(fullContent());

        assertThat(bytes).isNotEmpty();
        // must be a valid ZIP
        assertThat(unzip(bytes)).isNotEmpty();
    }

    // -------------------------------------------------------------------------

    private static ProjectExportContent fullContent() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");

        Project project = new Project("proj-1", "Test Project", "desc",
                Project.ProjectStatus.ACTIVE, now, now, "local", 1L);

        DataSource ds = new DataSource("ds-1", "proj-1", "OPC UA Source",
                Protocol.OPC_UA, SourceBasis.SCAN, "schema-1", 1,
                4840, "device://plc", "{}", null, true,
                RuntimeState.STOPPED, CredentialState.MISSING,
                "opc.tcp://localhost:4840/iotsim",
                now, now, "local", 1L);

        SchemaNode node = new SchemaNode("ns=2;s=Temp", null, "/Temperature",
                "Temperature", NodeKind.VARIABLE, DataType.FLOAT32,
                ValueRank.SCALAR, Access.READ, "°C", null);
        SchemaWithNodes schema = new SchemaWithNodes("schema-1", "ds-1", 1,
                OffsetDateTime.parse("2026-06-01T00:00:00Z"), List.of(node));
        Map<String, SchemaWithNodes> schemas = Map.of("ds-1", schema);

        Recording rec = new Recording("rec-1", "proj-1", "ds-1", 1, "SCAN_RECORD", "SCHEMA_AND_DATA",
                null, 100L, now, "local", 1L);

        Sample sample = new Sample("smp-1", "proj-1", "rec-1", "Sample A",
                "{}", List.of("tag1"), now, "local", 1L);

        return new ProjectExportContent(project, List.of(ds), schemas, List.of(rec), List.of(sample));
    }

    private static ProjectExportContent contentWithSecurityConfig(String securityConfig) {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        Project project = new Project("proj-sc", "Security Config Project", "desc",
                Project.ProjectStatus.ACTIVE, now, now, "local", 1L);
        DataSource ds = new DataSource("ds-sc", "proj-sc", "Secure Source",
                Protocol.OPC_UA, SourceBasis.SCAN, null, null,
                4840, "device://plc", "{}", securityConfig, false,
                RuntimeState.STOPPED, CredentialState.MISSING,
                "opc.tcp://localhost:4840/iotsim", now, now, "local", 1L);
        return new ProjectExportContent(project, List.of(ds), Map.of(), List.of(), List.of());
    }

    private static Map<String, String> unzip(byte[] bytes) {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return entries;
    }
}
