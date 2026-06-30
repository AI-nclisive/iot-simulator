package com.ainclusive.iotsim.domain.sample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.project.ProjectRow;
import com.ainclusive.iotsim.persistence.sample.SampleRepository;
import com.ainclusive.iotsim.persistence.sample.SampleRow;
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

/** Unit tests for {@link SampleImportExportService} (IS-070). */
class SampleImportExportServiceTest {

    private SampleRepository samples;
    private ValueTimelineRepository timeline;
    private ProjectRepository projects;
    private ObjectStore objectStore;
    private SampleImportExportService service;

    private static final String PROJECT = "p1";
    private static final String SAMPLE_ID = "smp-1";
    private static final String REC_ID = "rec-1";

    @BeforeEach
    void setUp() {
        samples = mock(SampleRepository.class);
        timeline = mock(ValueTimelineRepository.class);
        projects = mock(ProjectRepository.class);
        objectStore = mock(ObjectStore.class);
        service = new SampleImportExportService(samples, timeline, projects, objectStore,
                new ObjectMapper());
    }

    // ─── Export ─────────────────────────────────────────────────────────────

    @Test
    void exportProducesZipWithManifestAndTimeline() throws Exception {
        SampleRow row = row();
        given(samples.findById(SAMPLE_ID)).willReturn(Optional.of(row));
        given(timeline.readAll(REC_ID)).willReturn(values());
        given(objectStore.put(any(), any(), any(Long.class), any())).willReturn("samples/smp-1/export.zip");

        SampleBundle bundle = service.export(PROJECT, SAMPLE_ID);

        assertThat(bundle.contentType()).isEqualTo("application/zip");
        assertThat(bundle.filename()).isEqualTo("sample-" + SAMPLE_ID + ".zip");

        Map<String, String> zip = unzip(bundle.content());
        assertThat(zip).containsKeys("manifest.json", "value-timeline.json");
        assertThat(zip.get("manifest.json"))
                .contains("\"formatVersion\":\"1.0.0\"")
                .contains("\"sampleId\":\"" + SAMPLE_ID + "\"")
                .contains("\"projectId\":\"" + PROJECT + "\"");
        assertThat(zip.get("value-timeline.json")).contains("\"nodeId\":\"node-1\"");
    }

    @Test
    void exportStoresZipInObjectStore() {
        given(samples.findById(SAMPLE_ID)).willReturn(Optional.of(row()));
        given(timeline.readAll(any())).willReturn(List.of());
        given(objectStore.put(any(), any(), any(Long.class), any())).willReturn("key");

        service.export(PROJECT, SAMPLE_ID);

        verify(objectStore).put(eq("samples/" + SAMPLE_ID + "/export.zip"), any(), any(Long.class),
                eq("application/zip"));
    }

    @Test
    void exportThrows404ForMissingSample() {
        given(samples.findById("missing")).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.export(PROJECT, "missing"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void exportWithNoRecordingProducesEmptyTimeline() throws Exception {
        SampleRow noRecordingRow = new SampleRow(SAMPLE_ID, PROJECT, null, "Baseline",
                "{}", "[]", now(), now(), "local", 0L);
        given(samples.findById(SAMPLE_ID)).willReturn(Optional.of(noRecordingRow));
        given(objectStore.put(any(), any(), any(Long.class), any())).willReturn("key");

        SampleBundle bundle = service.export(PROJECT, SAMPLE_ID);
        Map<String, String> zip = unzip(bundle.content());
        assertThat(zip.get("value-timeline.json")).isEqualTo("[]");
    }

    // ─── openBundle ─────────────────────────────────────────────────────────

    @Test
    void openBundleReturnsPreviouslyStoredZip() {
        given(samples.findById(SAMPLE_ID)).willReturn(Optional.of(row()));
        byte[] zip = "PK fake-zip".getBytes(StandardCharsets.UTF_8);
        given(objectStore.get("samples/" + SAMPLE_ID + "/export.zip"))
                .willReturn(Optional.of(new ByteArrayInputStream(zip)));

        Optional<SampleBundle> bundle = service.openBundle(PROJECT, SAMPLE_ID);

        assertThat(bundle).isPresent();
        assertThat(bundle.get().contentType()).isEqualTo("application/zip");
    }

    @Test
    void openBundleReturnsEmptyWhenNotExported() {
        given(samples.findById(SAMPLE_ID)).willReturn(Optional.of(row()));
        given(objectStore.get(any())).willReturn(Optional.empty());

        assertThat(service.openBundle(PROJECT, SAMPLE_ID)).isEmpty();
    }

    // ─── Import ─────────────────────────────────────────────────────────────

    @Test
    void importCreatesSample() throws Exception {
        given(projects.findById(PROJECT)).willReturn(Optional.of(projectRow()));
        SampleRow created = row();
        given(samples.create(eq(PROJECT), any(), any(), any(), any(), eq("local")))
                .willReturn(created);

        byte[] zipContent = buildExportZip(PROJECT, SAMPLE_ID, REC_ID, "Baseline",
                List.of("env"), values());

        Sample result = service.importSample(PROJECT, zipContent, "local");

        assertThat(result).isNotNull();
        verify(samples).create(eq(PROJECT), eq(REC_ID), eq("Baseline"), any(), any(), eq("local"));
    }

    @Test
    void importThrows404ForMissingProject() {
        given(projects.findById("nope")).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.importSample("nope", "invalid".getBytes(), "local"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void importThrowsForMissingManifest() throws Exception {
        given(projects.findById(PROJECT)).willReturn(Optional.of(projectRow()));
        byte[] zipContent = buildZipWithoutManifest();
        assertThatThrownBy(() -> service.importSample(PROJECT, zipContent, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("manifest.json");
    }

    @Test
    void importThrowsForUnsupportedFormatVersion() throws Exception {
        given(projects.findById(PROJECT)).willReturn(Optional.of(projectRow()));
        byte[] zipContent = buildExportZipWithVersion("9.0.0");
        assertThatThrownBy(() -> service.importSample(PROJECT, zipContent, "local"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unsupported");
    }

    @Test
    void secretFieldsNeverAppearInExportZip() throws Exception {
        given(samples.findById(SAMPLE_ID)).willReturn(Optional.of(row()));
        given(timeline.readAll(any())).willReturn(values());
        given(objectStore.put(any(), any(), any(Long.class), any())).willReturn("key");

        SampleBundle bundle = service.export(PROJECT, SAMPLE_ID);
        Map<String, String> zip = unzip(bundle.content());
        String all = String.join("\n", zip.values()).toLowerCase();
        assertThat(all)
                .doesNotContain("password")
                .doesNotContain("secret")
                .doesNotContain("credential")
                .doesNotContain("privatekey");
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static SampleRow row() {
        return new SampleRow(SAMPLE_ID, PROJECT, REC_ID, "Baseline", "{}", "[\"env\"]",
                now(), now(), "local", 0L);
    }

    private static OffsetDateTime now() {
        return Instant.now().atOffset(ZoneOffset.UTC);
    }

    private static ProjectRow projectRow() {
        OffsetDateTime t = now();
        return new ProjectRow(PROJECT, "proj", null, "ACTIVE", t, t, "local", 0L);
    }

    private static List<NeutralValue> values() {
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        return List.of(
                new NeutralValue("node-1", t, 42.0, Quality.GOOD, null),
                new NeutralValue("node-1", t.plusSeconds(1), 43.0, Quality.GOOD, null));
    }

    private byte[] buildExportZip(String projectId, String sampleId, String recId,
            String name, List<String> tags, List<NeutralValue> vals) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        SampleExportManifest manifest = new SampleExportManifest(
                "1.0.0", sampleId, projectId, recId, name, "{}", tags, Instant.now(),
                vals.size(), List.of("node-1"));
        byte[] manifestBytes = mapper.writeValueAsBytes(manifest);

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
        SampleExportManifest manifest = new SampleExportManifest(
                version, SAMPLE_ID, PROJECT, REC_ID, "Baseline", "{}", List.of(),
                Instant.now(), 0, List.of());
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
