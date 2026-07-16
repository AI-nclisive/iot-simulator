package com.ainclusive.iotsim.domain.io;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.datasource.CredentialState;
import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.datasource.Protocol;
import com.ainclusive.iotsim.domain.datasource.RuntimeState;
import com.ainclusive.iotsim.domain.datasource.SourceBasis;
import com.ainclusive.iotsim.domain.project.Project;
import com.ainclusive.iotsim.domain.recording.Recording;
import com.ainclusive.iotsim.domain.sample.Sample;
import com.ainclusive.iotsim.domain.sample.SampleService;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import com.ainclusive.iotsim.protocolmodel.Access;
import com.ainclusive.iotsim.protocolmodel.DataType;
import com.ainclusive.iotsim.protocolmodel.NodeKind;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import com.ainclusive.iotsim.protocolmodel.ValueRank;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ProjectImportServiceTest {

    private final ObjectMapper json = new ObjectMapper();
    private final ProjectZipExporter exporter = new ProjectZipExporter(json);

    @Test
    void importCreatesProjectWithNameFromExport() {
        Fixture fixture = new Fixture();
        Project result = fixture.service().importProject(exportStream(simpleContent("My Project")), "local");

        assertThat(result.name()).isEqualTo("My Project");
        assertThat(fixture.createdProjects).hasSize(1);
    }

    @Test
    void importCreatesDataSourcesForEachExportedSource() {
        Fixture fixture = new Fixture();
        fixture.service().importProject(exportStream(fullContent()), "local");

        assertThat(fixture.insertedDataSources).hasSize(1);
        assertThat(fixture.insertedDataSources.get(0).name()).isEqualTo("OPC UA Source");
    }

    @Test
    void importSavesSchemaNodesForDataSources() {
        Fixture fixture = new Fixture();
        fixture.service().importProject(exportStream(fullContent()), "local");

        assertThat(fixture.savedSchemas).hasSize(1);
        assertThat(fixture.savedSchemas.get(0)).isNotEmpty();
    }

    @Test
    void importCreatesRecordingMetadataRows() {
        Fixture fixture = new Fixture();
        fixture.service().importProject(exportStream(fullContent()), "local");

        assertThat(fixture.createdRecordings).hasSize(1);
    }

    @Test
    void importCreatesSampleRows() {
        Fixture fixture = new Fixture();
        fixture.service().importProject(exportStream(fullContent()), "local");

        assertThat(fixture.createdSamples).hasSize(1);
        assertThat(fixture.createdSamples.get(0).name()).isEqualTo("Sample A");
    }

    @Test
    void rejectsZipWithoutManifest() {
        Fixture fixture = new Fixture();
        byte[] emptyZip = makeZip(Map.of("project.json", "{\"name\":\"x\"}"));
        assertThatThrownBy(() -> fixture.service().importProject(new ByteArrayInputStream(emptyZip), "local"))
                .isInstanceOf(ProjectImportException.class)
                .hasMessageContaining("manifest.json");
    }

    @Test
    void rejectsUnsupportedMajorVersion() {
        Fixture fixture = new Fixture();
        byte[] zip = makeZipWithManifestVersion("99.0.0");
        assertThatThrownBy(() -> fixture.service().importProject(new ByteArrayInputStream(zip), "local"))
                .isInstanceOf(ProjectImportException.class)
                .hasMessageContaining("Unsupported");
    }

    @Test
    void rejectsMissingFormatVersion() {
        Fixture fixture = new Fixture();
        // manifest.json is present but has no formatVersion field
        byte[] zip = makeZip(Map.of(
                "manifest.json", "{\"exportedAt\":\"2026-06-01T00:00:00Z\"}",
                "project.json", "{\"name\":\"x\"}"));
        assertThatThrownBy(() -> fixture.service().importProject(new ByteArrayInputStream(zip), "local"))
                .isInstanceOf(ProjectImportException.class)
                .hasMessageContaining("formatVersion");
    }

    @Test
    void rejectsNonZipInput() {
        Fixture fixture = new Fixture();
        byte[] notZip = "not a zip".getBytes();
        assertThatThrownBy(() -> fixture.service().importProject(new ByteArrayInputStream(notZip), "local"))
                .isInstanceOf(ProjectImportException.class);
    }

    @Test
    void rejectsChecksumMismatch() {
        // Build a valid export ZIP then tamper with project.json content
        // by creating a new ZIP with the same manifest but different project bytes.
        byte[] goodZip = new ProjectZipExporter(json).toBytes(simpleContent("Original"));

        // Extract entries and corrupt project.json
        Map<String, String> entries = new java.util.LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(goodZip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                String content = new String(zis.readAllBytes(), StandardCharsets.UTF_8);
                if ("project.json".equals(e.getName())) {
                    // tamper: change the name so checksum no longer matches
                    content = content.replace("Original", "Tampered");
                }
                entries.put(e.getName(), content);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        byte[] tamperedZip = makeZip(entries);

        Fixture fixture = new Fixture();
        assertThatThrownBy(() -> fixture.service().importProject(new ByteArrayInputStream(tamperedZip), "local"))
                .isInstanceOf(ProjectImportException.class)
                .hasMessageContaining("Integrity check failed");
    }

    @Test
    void preservesEnabledStateOfDataSources() {
        Fixture fixture = new Fixture();
        fixture.service().importProject(exportStream(fullContent()), "local");

        // fullContent() has enabled=true for the one data source.
        // After insert (enabled=false by default) + update should have been called.
        assertThat(fixture.updateCalledCount).isGreaterThan(0);
    }

    @Test
    void importPreservesSecurityConfigHashes() {
        // Build a content whose data source carries a hashed securityConfig,
        // export it, then import it — the hash must survive the round-trip.
        String storedHash = "{\"userTokens\":{\"anonymous\":false,\"username\":{\"enabled\":true,"
                + "\"users\":[{\"username\":\"op\",\"passwordHash\":\"pbkdf2-sha256$1$aa$bb\"}]}}}";
        ProjectExportContent content = contentWithSecurityConfig(storedHash);

        Fixture fixture = new Fixture();
        fixture.service().importProject(exportStream(content), "local");

        assertThat(fixture.insertedDataSources).hasSize(1);
        DataSourceRow imported = fixture.insertedDataSources.get(0);
        assertThat(imported.securityConfig())
                .isNotNull()
                .contains("passwordHash")
                .isEqualTo(storedHash);
    }

    // -------------------------------------------------------------------------

    private InputStream exportStream(ProjectExportContent content) {
        return new ByteArrayInputStream(exporter.toBytes(content));
    }

    private static ProjectExportContent simpleContent(String projectName) {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        Project project = new Project("proj-x", projectName, "desc",
                Project.ProjectStatus.ACTIVE, now, now, "local", 1L);
        return new ProjectExportContent(project, List.of(), Map.of(), List.of(), List.of());
    }

    private static ProjectExportContent fullContent() {
        Instant now = Instant.parse("2026-06-01T00:00:00Z");
        Project project = new Project("proj-1", "My Project", "desc",
                Project.ProjectStatus.ACTIVE, now, now, "local", 1L);

        DataSource ds = new DataSource("ds-1", "proj-1", "OPC UA Source",
                Protocol.OPC_UA, SourceBasis.SCAN, "schema-1", 1,
                4840, "device://plc", "{}", null, true,
                RuntimeState.STOPPED, CredentialState.MISSING,
                "opc.tcp://localhost:4840/iotsim", now, now, "local", 1L);

        SchemaNode node = new SchemaNode("ns=2;s=Temp", null, "/Temp", "Temp",
                NodeKind.VARIABLE, DataType.FLOAT32, ValueRank.SCALAR, Access.READ, null, null);
        SchemaWithNodes schema = new SchemaWithNodes("schema-1", "ds-1", 1,
                OffsetDateTime.parse("2026-06-01T00:00:00Z"), List.of(node));

        Recording rec = new Recording("rec-1", "proj-1", "ds-1", 1, "SCAN_RECORD", "SCHEMA_AND_DATA",
                null, 100L, 0L, now, "local", 1L, null, false);

        Sample sample = new Sample("smp-1", "proj-1", "rec-1", "Sample A",
                "{}", List.of("tag1"), now, "local", 1L);

        return new ProjectExportContent(project, List.of(ds), Map.of("ds-1", schema),
                List.of(rec), List.of(sample));
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

    /** Builds a ZIP with a manifest.json that has the given formatVersion. */
    private static byte[] makeZipWithManifestVersion(String version) {
        String manifest = "{\"formatVersion\":\"" + version + "\",\"exportedAt\":\"2026-06-01T00:00:00Z\"}";
        return makeZip(Map.of(
                "manifest.json", manifest,
                "project.json", "{\"name\":\"x\"}"));
    }

    /** Builds a ZIP from a map of filename → content. */
    private static byte[] makeZip(Map<String, String> entries) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (Map.Entry<String, String> e : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(e.getKey()));
                zos.write(e.getValue().getBytes(StandardCharsets.UTF_8));
                zos.closeEntry();
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        return out.toByteArray();
    }

    // -------------------------------------------------------------------------

    private class Fixture {
        final List<Project> createdProjects = new ArrayList<>();
        final List<DataSourceRow> insertedDataSources = new ArrayList<>();
        final List<List<SchemaNode>> savedSchemas = new ArrayList<>();
        final List<RecordingRow> createdRecordings = new ArrayList<>();
        final List<Sample> createdSamples = new ArrayList<>();
        int updateCalledCount = 0;

        private final AtomicInteger counter = new AtomicInteger();

        DataSourceRepository dsRepo() {
            return new DataSourceRepository() {
                @Override
                public DataSourceRow insert(String projectId, String name, String protocol,
                        String basis, int simulatorPort, String realDeviceEndpoint,
                        String runtimeConfigJson, String securityConfigJson, String createdBy) {
                    Instant now = Instant.now();
                    OffsetDateTime odt = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
                    DataSourceRow row = new DataSourceRow("new-ds-" + counter.incrementAndGet(),
                            projectId, name, protocol, basis, null, null, simulatorPort, realDeviceEndpoint,
                            runtimeConfigJson, securityConfigJson, false, odt, odt, createdBy, 0L);
                    insertedDataSources.add(row);
                    return row;
                }

                @Override
                public Optional<DataSourceRow> duplicate(String sourceId, String newName, String createdBy) {
                    return Optional.empty();
                }

                @Override
                public List<DataSourceRow> findByProject(String projectId) { return List.of(); }

                @Override
                public List<DataSourceRow> findByProjectPaged(String projectId, String protocol,
                        OffsetDateTime afterAt, String afterId, int limit) { return List.of(); }

                @Override
                public Optional<DataSourceRow> findById(String id) { return Optional.empty(); }

                @Override
                public Optional<DataSourceRow> update(String id, String name, int simulatorPort,
                        String realDeviceEndpoint, String runtimeConfigJson, String securityConfigJson,
                        boolean enabled, long expectedVersion) {
                    updateCalledCount++;
                    // Return the last inserted row with the updated enabled state.
                    if (insertedDataSources.isEmpty()) {
                        return Optional.empty();
                    }
                    DataSourceRow last = insertedDataSources.get(insertedDataSources.size() - 1);
                    OffsetDateTime odt = OffsetDateTime.now(ZoneOffset.UTC);
                    return Optional.of(new DataSourceRow(last.id(), last.projectId(), name,
                            last.protocol(), last.basis(), null, null, simulatorPort, realDeviceEndpoint,
                            runtimeConfigJson, securityConfigJson, enabled, last.createdAt(), odt, last.createdBy(),
                            expectedVersion + 1));
                }

                @Override
                public boolean deleteById(String id) { return false; }
            };
        }

        SchemaRepository schemaRepo() {
            return new SchemaRepository() {
                @Override
                public Optional<SchemaWithNodes> findCurrent(String dataSourceId) {
                    return Optional.empty();
                }

                @Override
                public SchemaWithNodes saveNewVersion(String dataSourceId, List<SchemaNode> nodes) {
                    savedSchemas.add(new ArrayList<>(nodes));
                    return new SchemaWithNodes("s-" + counter.incrementAndGet(), dataSourceId, 1,
                            OffsetDateTime.now(ZoneOffset.UTC), nodes);
                }
            };
        }

        RecordingRepository recRepo() {
            return new RecordingRepository() {
                @Override
                public RecordingRow create(String projectId, String dataSourceId, int schemaVersion,
                        String origin, String scanType, String name, String createdBy) {
                    Instant now = Instant.now();
                    OffsetDateTime odt = OffsetDateTime.ofInstant(now, ZoneOffset.UTC);
                    RecordingRow row = new RecordingRow("new-rec-" + counter.incrementAndGet(),
                            projectId, dataSourceId, schemaVersion, origin, scanType,
                            name, null, null, 0L, 0L, odt, odt, createdBy, 0L);
                    createdRecordings.add(row);
                    return row;
                }

                @Override
                public Optional<RecordingRow> findById(String id) { return Optional.empty(); }

                @Override
                public List<RecordingRow> findByProject(String projectId) { return List.of(); }

                @Override
                public List<RecordingRow> findByProjectPaged(String projectId,
                        OffsetDateTime afterAt, String afterId, int limit) { return List.of(); }

                @Override
                public RecordingRow finalizeStats(String id, OffsetDateTime timeStart,
                        OffsetDateTime timeEnd, long valueCount, long sizeBytes) { return null; }

                @Override
                public boolean deleteById(String id) { throw new UnsupportedOperationException(); }

                @Override
                public long countByProject(String projectId) { throw new UnsupportedOperationException(); }
            };
        }

        SampleService sampleSvc() {
            return new SampleService(null, null, null, null) {
                @Override
                public Sample create(String projectId, String derivedFromRecordingId,
                        String name, String selection, List<String> tags, String actor) {
                    Instant now = Instant.now();
                    Sample s = new Sample("new-smp-" + counter.incrementAndGet(),
                            projectId, derivedFromRecordingId, name, selection, tags, now, actor, 0L);
                    createdSamples.add(s);
                    return s;
                }
            };
        }

        ProjectImportService service() {
            return new ProjectImportService(fakeProjectService(), dsRepo(), schemaRepo(),
                    recRepo(), sampleSvc(), json);
        }

        private com.ainclusive.iotsim.domain.project.ProjectService fakeProjectService() {
            return new com.ainclusive.iotsim.domain.project.ProjectService(null, null, null, null) {
                @Override
                public Project create(String name, String description, String actor) {
                    Instant now = Instant.now();
                    Project p = new Project("new-proj-" + counter.incrementAndGet(),
                            name, description, Project.ProjectStatus.ACTIVE, now, now, actor, 0L);
                    createdProjects.add(p);
                    return p;
                }
            };
        }
    }
}
