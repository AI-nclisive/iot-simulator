package com.ainclusive.iotsim.domain.recording;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.timeline.ValueTimelineRepository;
import com.ainclusive.iotsim.platform.storage.ObjectStore;
import com.ainclusive.iotsim.protocolmodel.NeutralValue;
import com.ainclusive.iotsim.protocolmodel.Quality;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/** Unit tests for {@link RecordingImportExportService} (IS-070). */
class RecordingImportExportServiceTest {

    private RecordingRepository recordings;
    private ValueTimelineRepository timeline;
    private ProjectRepository projects;
    private DataSourceRepository dataSources;
    private ObjectStore objectStore;
    private RecordingImportExportService service;

    private static final String PROJECT = "p1";
    private static final String REC_ID = "rec-1";

    @BeforeEach
    void setUp() {
        recordings = mock(RecordingRepository.class);
        timeline = mock(ValueTimelineRepository.class);
        projects = mock(ProjectRepository.class);
        dataSources = mock(DataSourceRepository.class);
        objectStore = mock(ObjectStore.class);
        given(dataSources.findById(org.mockito.ArgumentMatchers.anyString()))
                .willReturn(Optional.of(dataSourceRow()));
        service = new RecordingImportExportService(recordings, timeline, projects, dataSources,
                objectStore, new ObjectMapper());
    }

    // ─── Export ─────────────────────────────────────────────────────────────

    @Test
    void exportProducesZipWithManifestAndTimeline() throws Exception {
        RecordingRow row = row();
        given(recordings.findById(REC_ID)).willReturn(Optional.of(row));
        given(timeline.readAll(REC_ID)).willReturn(values());
        given(objectStore.put(any(), any(), any(Long.class), any())).willReturn("recordings/rec-1/export.zip");

        RecordingBundle bundle = service.export(PROJECT, REC_ID);

        assertThat(bundle.contentType()).isEqualTo("application/zip");
        assertThat(bundle.filename()).isEqualTo("recording-" + REC_ID + ".zip");

        Map<String, String> zip = unzip(bundle.content());
        assertThat(zip).containsKeys("manifest.json", "value-timeline.json");
        assertThat(zip.get("manifest.json"))
                .contains("\"formatVersion\":\"1.0.0\"")
                .contains("\"recordingId\":\"" + REC_ID + "\"")
                .contains("\"projectId\":\"" + PROJECT + "\"");
        assertThat(zip.get("value-timeline.json")).contains("\"nodeId\":\"node-1\"");
    }

    @Test
    void exportStoresZipInObjectStore() {
        given(recordings.findById(REC_ID)).willReturn(Optional.of(row()));
        given(timeline.readAll(REC_ID)).willReturn(List.of());
        given(objectStore.put(any(), any(), any(Long.class), any())).willReturn("key");

        service.export(PROJECT, REC_ID);

        verify(objectStore).put(eq("recordings/" + REC_ID + "/export.zip"), any(), any(Long.class),
                eq("application/zip"));
    }

    @Test
    void exportThrows404ForMissingRecording() {
        given(recordings.findById("missing")).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.export(PROJECT, "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void exportThrows404WhenRecordingBelongsToDifferentProject() {
        given(recordings.findById(REC_ID)).willReturn(Optional.of(
                row("other-project", REC_ID)));
        assertThatThrownBy(() -> service.export(PROJECT, REC_ID))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ─── openBundle ─────────────────────────────────────────────────────────

    @Test
    void openBundleReturnsPreviouslyStoredZip() {
        given(recordings.findById(REC_ID)).willReturn(Optional.of(row()));
        byte[] zip = "PK fake-zip".getBytes(StandardCharsets.UTF_8);
        given(objectStore.get("recordings/" + REC_ID + "/export.zip"))
                .willReturn(Optional.of(new ByteArrayInputStream(zip)));

        Optional<RecordingBundle> bundle = service.openBundle(PROJECT, REC_ID);

        assertThat(bundle).isPresent();
        assertThat(bundle.get().contentType()).isEqualTo("application/zip");
    }

    @Test
    void openBundleReturnsEmptyWhenNotExported() {
        given(recordings.findById(REC_ID)).willReturn(Optional.of(row()));
        given(objectStore.get(any())).willReturn(Optional.empty());

        assertThat(service.openBundle(PROJECT, REC_ID)).isEmpty();
    }

    // ─── Import ─────────────────────────────────────────────────────────────

    @Test
    void importCreatesRecordingWithImportedOrigin() throws Exception {
        given(projects.findById(PROJECT)).willReturn(Optional.of(projectRow()));
        RecordingRow created = row();
        RecordingRow finalized = row();
        given(recordings.create(eq(PROJECT), any(), any(), any(Integer.class), eq("IMPORTED"), eq("SCHEMA_AND_DATA"), any(), eq("local")))
                .willReturn(created);
        given(recordings.finalizeStats(eq(REC_ID), any(), any(), any(Long.class), any(Long.class)))
                .willReturn(finalized);
        given(timeline.append(any(), any())).willReturn(2L);

        byte[] zipContent = buildExportZip(PROJECT, REC_ID, "ds-1", 1, values());

        Recording result = service.importRecording(PROJECT, zipContent, "local");

        assertThat(result).isNotNull();
        verify(recordings).create(eq(PROJECT), eq("ds-1"), eq("OPC_UA"), eq(1), eq("IMPORTED"), eq("SCHEMA_AND_DATA"), any(), eq("local"));
    }

    @Test
    void importAppendsValueTimeline() throws Exception {
        given(projects.findById(PROJECT)).willReturn(Optional.of(projectRow()));
        RecordingRow created = row();
        given(recordings.create(any(), any(), any(), any(Integer.class), any(), any(), any(), any())).willReturn(created);
        given(recordings.finalizeStats(any(), any(), any(), any(Long.class), any(Long.class))).willReturn(created);

        byte[] zipContent = buildExportZip(PROJECT, REC_ID, "ds-1", 1, values());
        service.importRecording(PROJECT, zipContent, "local");

        verify(timeline).append(eq(REC_ID), any());
    }

    @Test
    void importForwardsNameFromManifestToCreate() throws Exception {
        given(projects.findById(PROJECT)).willReturn(Optional.of(projectRow()));
        RecordingRow created = row();
        given(recordings.create(any(), any(), any(), any(Integer.class), any(), any(), any(), any())).willReturn(created);
        given(recordings.finalizeStats(any(), any(), any(), any(Long.class), any(Long.class))).willReturn(created);

        byte[] zipContent = buildExportZipWithName("My Named Recording");
        service.importRecording(PROJECT, zipContent, "local");

        verify(recordings).create(eq(PROJECT), any(), any(), any(Integer.class), eq("IMPORTED"),
                eq("SCHEMA_AND_DATA"), eq("My Named Recording"), eq("local"));
    }

    @Test
    void importThrows404ForMissingProject() {
        given(projects.findById("nope")).willReturn(Optional.empty());
        byte[] zipContent = "invalid".getBytes();
        assertThatThrownBy(() -> service.importRecording("nope", zipContent, "local"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void importThrowsForMissingManifest() throws Exception {
        given(projects.findById(PROJECT)).willReturn(Optional.of(projectRow()));
        byte[] zipContent = buildZipWithoutManifest();
        assertThatThrownBy(() -> service.importRecording(PROJECT, zipContent, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manifest.json");
    }

    @Test
    void importSucceedsForUnknownDataSourceWhenProtocolValid() throws Exception {
        // IS-160: recordings are scoped to a protocol type, not to the exact data-source
        // instance they were captured from — an unknown/stale dataSourceId no longer blocks
        // import as long as a valid protocol is present.
        given(projects.findById(PROJECT)).willReturn(Optional.of(projectRow()));
        given(dataSources.findById("does-not-exist")).willReturn(Optional.empty());
        RecordingRow created = row();
        given(recordings.create(eq(PROJECT), any(), eq("OPC_UA"), any(Integer.class), eq("IMPORTED"),
                eq("SCHEMA_AND_DATA"), any(), eq("local"))).willReturn(created);
        given(recordings.finalizeStats(any(), any(), any(), any(Long.class), any(Long.class)))
                .willReturn(created);

        byte[] zipContent = buildExportZip(PROJECT, REC_ID, "does-not-exist", 1, values());

        Recording result = service.importRecording(PROJECT, zipContent, "local");

        assertThat(result).isNotNull();
        // The unknown dataSourceId is dropped (not surfaced as an FK to a nonexistent row).
        verify(recordings).create(eq(PROJECT), isNull(), eq("OPC_UA"), any(Integer.class),
                eq("IMPORTED"), eq("SCHEMA_AND_DATA"), any(), eq("local"));
    }

    @Test
    void importThrowsForMissingProtocol() throws Exception {
        given(projects.findById(PROJECT)).willReturn(Optional.of(projectRow()));

        ObjectMapper mapper = new ObjectMapper();
        RecordingExportManifest manifest = new RecordingExportManifest(
                "1.0.0", REC_ID, PROJECT, "ds-1", null, 1, "SCAN_RECORD",
                null, Instant.now(), null, null, 0, List.of());
        byte[] manifestBytes = mapper.writeValueAsBytes(manifest);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifestBytes);
            zip.closeEntry();
        }

        assertThatThrownBy(() -> service.importRecording(PROJECT, buf.toByteArray(), "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("protocol");
    }

    @Test
    void importThrowsForInvalidProtocol() throws Exception {
        given(projects.findById(PROJECT)).willReturn(Optional.of(projectRow()));

        ObjectMapper mapper = new ObjectMapper();
        RecordingExportManifest manifest = new RecordingExportManifest(
                "1.0.0", REC_ID, PROJECT, "ds-1", "BLUETOOTH", 1, "SCAN_RECORD",
                null, Instant.now(), null, null, 0, List.of());
        byte[] manifestBytes = mapper.writeValueAsBytes(manifest);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifestBytes);
            zip.closeEntry();
        }

        assertThatThrownBy(() -> service.importRecording(PROJECT, buf.toByteArray(), "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown protocol");
    }

    @Test
    void importThrowsForUnsupportedFormatVersion() throws Exception {
        given(projects.findById(PROJECT)).willReturn(Optional.of(projectRow()));
        byte[] zipContent = buildExportZipWithVersion("9.0.0");
        assertThatThrownBy(() -> service.importRecording(PROJECT, zipContent, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");
    }

    @Test
    void importThrowsForCorruptZip() {
        given(projects.findById(PROJECT)).willReturn(Optional.of(projectRow()));
        byte[] garbage = "this is not a zip file at all, just plain text".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> service.importRecording(PROJECT, garbage, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not a valid ZIP archive");
    }

    @Test
    void importThrowsForMalformedManifestJson() throws Exception {
        given(projects.findById(PROJECT)).willReturn(Optional.of(projectRow()));

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write("{ this is not valid json ]]".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertThatThrownBy(() -> service.importRecording(PROJECT, buf.toByteArray(), "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("malformed manifest.json");
    }

    @Test
    void importThrowsForMissingSchemaVersion() throws Exception {
        given(projects.findById(PROJECT)).willReturn(Optional.of(projectRow()));

        ObjectMapper mapper = new ObjectMapper();
        RecordingExportManifest manifest = new RecordingExportManifest(
                "1.0.0", REC_ID, PROJECT, "ds-1", "OPC_UA", 0, "SCAN_RECORD",
                null, Instant.now(), null, null, 0, List.of());
        byte[] manifestBytes = mapper.writeValueAsBytes(manifest);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifestBytes);
            zip.closeEntry();
        }

        assertThatThrownBy(() -> service.importRecording(PROJECT, buf.toByteArray(), "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("schemaVersion");
    }

    @Test
    void secretFieldsNeverAppearInExportZip() throws Exception {
        given(recordings.findById(REC_ID)).willReturn(Optional.of(row()));
        given(timeline.readAll(REC_ID)).willReturn(values());
        given(objectStore.put(any(), any(), any(Long.class), any())).willReturn("key");

        RecordingBundle bundle = service.export(PROJECT, REC_ID);
        Map<String, String> zip = unzip(bundle.content());
        String all = String.join("\n", zip.values()).toLowerCase();
        assertThat(all)
                .doesNotContain("password")
                .doesNotContain("secret")
                .doesNotContain("credential")
                .doesNotContain("privatekey");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static RecordingRow row() {
        return row(PROJECT, REC_ID);
    }

    private static RecordingRow row(String projectId, String id) {
        OffsetDateTime now = Instant.now().atOffset(ZoneOffset.UTC);
        return new RecordingRow(id, projectId, "ds-1", "OPC_UA", 1, "SCAN_RECORD", "SCHEMA_AND_DATA",
                null, now, now, 2L, 0L, now, now, "local", 0L);
    }

    private static com.ainclusive.iotsim.persistence.project.ProjectRow projectRow() {
        OffsetDateTime now = Instant.now().atOffset(ZoneOffset.UTC);
        // ProjectRow(id, name, description, status, createdAt, updatedAt, createdBy, version)
        return new com.ainclusive.iotsim.persistence.project.ProjectRow(
                PROJECT, "proj", null, "ACTIVE", now, now, "local", 0L);
    }

    private static DataSourceRow dataSourceRow() {
        OffsetDateTime now = Instant.now().atOffset(ZoneOffset.UTC);
        return new DataSourceRow("ds-1", PROJECT, "source", "OPC_UA", "SIMULATED", null, null,
                4840, null, "{}", "{}", true, now, now, "local", 0L);
    }

    private static List<NeutralValue> values() {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        return List.of(
                new NeutralValue("node-1", t, 42.0, Quality.GOOD, null),
                new NeutralValue("node-1", t.plusSeconds(1), 43.0, Quality.GOOD, null));
    }

    private byte[] buildExportZip(String projectId, String recId, String dataSourceId,
            int schemaVersion, List<NeutralValue> vals) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RecordingExportManifest manifest = new RecordingExportManifest(
                "1.0.0", recId, projectId, dataSourceId, "OPC_UA", schemaVersion,
                "SCAN_RECORD", null, Instant.now(), null, null, vals.size(), List.of("node-1"));
        byte[] manifestBytes = mapper.writeValueAsBytes(manifest);

        // Build a minimal value-timeline JSON
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vals.size(); i++) {
            if (i > 0) {
                sb.append(",");
            }
            NeutralValue v = vals.get(i);
            sb.append("{\"nodeId\":\"").append(v.nodeId()).append("\",")
              .append("\"sourceTime\":\"").append(v.sourceTime()).append("\",")
              .append("\"value\":42.0,")
              .append("\"quality\":\"GOOD\",")
              .append("\"qualityReason\":null}");
        }
        sb.append("]");
        byte[] timelineBytes = sb.toString().getBytes(StandardCharsets.UTF_8);

        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifestBytes);
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("value-timeline.json"));
            zip.write(timelineBytes);
            zip.closeEntry();
        }
        return buf.toByteArray();
    }

    private static byte[] buildZipWithoutManifest() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf)) {
            zip.putNextEntry(new ZipEntry("value-timeline.json"));
            zip.write("[]".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return buf.toByteArray();
    }

    private byte[] buildExportZipWithVersion(String version) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RecordingExportManifest manifest = new RecordingExportManifest(
                version, REC_ID, PROJECT, "ds-1", "OPC_UA", 1, "SCAN_RECORD",
                null, Instant.now(), null, null, 0, List.of());
        byte[] manifestBytes = mapper.writeValueAsBytes(manifest);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifestBytes);
            zip.closeEntry();
        }
        return buf.toByteArray();
    }

    private byte[] buildExportZipWithName(String name) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        RecordingExportManifest manifest = new RecordingExportManifest(
                "1.0.0", REC_ID, PROJECT, "ds-1", "OPC_UA", 1, "SCAN_RECORD",
                name, Instant.now(), null, null, 0, List.of());
        byte[] manifestBytes = mapper.writeValueAsBytes(manifest);
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(manifestBytes);
            zip.closeEntry();
        }
        return buf.toByteArray();
    }

    private static Map<String, String> unzip(InputStream in) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }
}
