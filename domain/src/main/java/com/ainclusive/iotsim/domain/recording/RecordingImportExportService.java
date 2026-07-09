package com.ainclusive.iotsim.domain.recording;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
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
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Export and import of recording artifacts as ZIP bundles (IS-070,
 * backend-specs/06_ARTIFACT_FORMATS.md §"Recording / sample export").
 *
 * <p>Export: assembles a ZIP containing {@code manifest.json} and
 * {@code value-timeline.json}, stores it in the {@link ObjectStore}, and returns a
 * {@link RecordingBundle} for streaming to the HTTP client.
 *
 * <p>Import: reads a ZIP from the HTTP upload, validates the manifest format version,
 * creates a new {@link Recording} row (with origin {@code IMPORTED}), and re-inserts
 * all value-timeline rows. The imported recording belongs to the target project and
 * keeps the original {@code dataSourceId} from the manifest (the caller supplies it
 * so the FE can map to an existing source if desired; if the source does not exist in
 * the target project the row is still created — any replay or evidence use-case will
 * surface the mismatch).
 */
@Service
public class RecordingImportExportService {

    static final String FORMAT_VERSION = RecordingExportManifest.FORMAT_VERSION;

    private final RecordingRepository recordings;
    private final ValueTimelineRepository timeline;
    private final ProjectRepository projects;
    private final ObjectStore objectStore;
    private final ObjectMapper json;

    public RecordingImportExportService(RecordingRepository recordings,
            ValueTimelineRepository timeline, ProjectRepository projects,
            ObjectStore objectStore, ObjectMapper json) {
        this.recordings = recordings;
        this.timeline = timeline;
        this.projects = projects;
        this.objectStore = objectStore;
        this.json = json;
    }

    // ─── Export ─────────────────────────────────────────────────────────────

    /**
     * Exports a recording to a ZIP bundle and stores it in the object store.
     *
     * @return a {@link RecordingBundle} whose {@code content} stream the controller
     *         pipes directly to the HTTP response
     */
    public RecordingBundle export(String projectId, String recordingId) {
        RecordingRow row = requireRecording(projectId, recordingId);

        List<NeutralValue> values = timeline.readAll(recordingId);
        Instant timeStart = values.isEmpty() ? null : values.get(0).sourceTime();
        Instant timeEnd = values.isEmpty() ? null : values.get(values.size() - 1).sourceTime();

        List<String> nodeIds = values.stream().map(NeutralValue::nodeId).distinct().toList();

        RecordingExportManifest manifest = new RecordingExportManifest(
                FORMAT_VERSION,
                row.id(),
                row.projectId(),
                row.dataSourceId(),
                row.schemaVersion(),
                row.origin(),
                row.name(),
                Instant.now(),
                timeStart,
                timeEnd,
                values.size(),
                nodeIds);

        byte[] zipBytes = buildZip(manifest, values);
        String key = "recordings/" + recordingId + "/export.zip";
        objectStore.put(key, new ByteArrayInputStream(zipBytes), zipBytes.length, "application/zip");

        String filename = "recording-" + recordingId + ".zip";
        return new RecordingBundle(new ByteArrayInputStream(zipBytes), "application/zip", filename);
    }

    /**
     * Returns a previously-exported bundle from the object store, or empty if not yet exported.
     * Callers may re-export by calling {@link #export} again.
     */
    public Optional<RecordingBundle> openBundle(String projectId, String recordingId) {
        requireRecording(projectId, recordingId);
        String key = "recordings/" + recordingId + "/export.zip";
        return objectStore.get(key).map(in ->
                new RecordingBundle(in, "application/zip", "recording-" + recordingId + ".zip"));
    }

    // ─── Import ─────────────────────────────────────────────────────────────

    /**
     * Imports a recording from a ZIP bundle (the reverse of {@link #export}).
     *
     * @param projectId target project (must exist)
     * @param zipContent raw bytes of the export ZIP
     * @param actor      user performing the import
     * @return the newly-created {@link Recording}
     * @throws IllegalArgumentException if the ZIP is malformed or its format version is unsupported
     */
    @Transactional
    public Recording importRecording(String projectId, byte[] zipContent, String actor) {
        requireProject(projectId);

        ImportPayload payload = parseZip(zipContent);
        RecordingExportManifest manifest = payload.manifest();
        validateFormatVersion(manifest.formatVersion());

        RecordingRow row = recordings.create(
                projectId,
                manifest.dataSourceId(),
                manifest.schemaVersion(),
                "IMPORTED",
                "SCHEMA_AND_DATA",
                manifest.name(),
                actor);

        if (!payload.values().isEmpty()) {
            timeline.append(row.id(), payload.values());
        }

        long count = payload.values().size();
        Instant start = payload.values().isEmpty() ? null : payload.values().get(0).sourceTime();
        Instant end = payload.values().isEmpty() ? null
                : payload.values().get(payload.values().size() - 1).sourceTime();

        java.time.OffsetDateTime oStart = toOffsetDateTime(start);
        java.time.OffsetDateTime oEnd = toOffsetDateTime(end);

        return map(recordings.finalizeStats(row.id(), oStart, oEnd, count, 0L));
    }

    // ─── ZIP build & parse ──────────────────────────────────────────────────

    private byte[] buildZip(RecordingExportManifest manifest, List<NeutralValue> values) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf)) {
            putEntry(zip, "manifest.json", json.writeValueAsBytes(manifest));
            putEntry(zip, "value-timeline.json", json.writeValueAsBytes(toJsonArray(values)));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to build recording export ZIP", e);
        }
        return buf.toByteArray();
    }

    private record ImportPayload(RecordingExportManifest manifest, List<NeutralValue> values) {}

    /** Maximum decompressed size per ZIP entry (100 MiB). Prevents ZIP-bomb attacks. */
    private static final long MAX_ENTRY_BYTES = 100L * 1024 * 1024;

    private ImportPayload parseZip(byte[] zipContent) {
        RecordingExportManifest manifest = null;
        List<NeutralValue> values = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipContent))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                byte[] bytes = readBounded(zip, entry.getName());
                if ("manifest.json".equals(entry.getName())) {
                    manifest = json.readValue(bytes, RecordingExportManifest.class);
                } else if ("value-timeline.json".equals(entry.getName())) {
                    values = parseValues(bytes);
                }
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to read recording import ZIP: " + e.getMessage(), e);
        }
        if (manifest == null) {
            throw new IllegalArgumentException("import ZIP is missing manifest.json");
        }
        return new ImportPayload(manifest, values != null ? values : List.of());
    }

    /**
     * Reads at most {@link #MAX_ENTRY_BYTES} from an {@link InputStream}, throwing
     * {@link IllegalArgumentException} if the entry exceeds the limit.
     */
    private static byte[] readBounded(InputStream in, String entryName) throws IOException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] block = new byte[8192];
        long total = 0;
        int read;
        while ((read = in.read(block)) != -1) {
            total += read;
            if (total > MAX_ENTRY_BYTES) {
                throw new IllegalArgumentException(
                        "ZIP entry '" + entryName + "' exceeds the maximum allowed size of "
                        + MAX_ENTRY_BYTES + " bytes");
            }
            buf.write(block, 0, read);
        }
        return buf.toByteArray();
    }

    private List<NeutralValue> parseValues(byte[] bytes) {
        List<NeutralValue> result = new ArrayList<>();
        try {
            var array = json.readTree(bytes);
            if (!array.isArray()) {
                return result;
            }
            for (var node : array) {
                String nodeId = node.path("nodeId").asString(null);
                String sourceTimeStr = node.path("sourceTime").asString(null);
                if (nodeId == null || sourceTimeStr == null) {
                    continue;
                }
                Instant sourceTime = Instant.parse(sourceTimeStr);
                Object value = parseValue(node.path("value"));
                String qualityStr = node.path("quality").asString("GOOD");
                Quality quality;
                try {
                    quality = Quality.valueOf(qualityStr);
                } catch (IllegalArgumentException e) {
                    quality = Quality.GOOD;
                }
                String qualityReason = node.path("qualityReason").isNull()
                        ? null : node.path("qualityReason").asString(null);
                result.add(new NeutralValue(nodeId, sourceTime, value, quality, qualityReason));
            }
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("malformed value-timeline.json: " + e.getMessage(), e);
        }
        return result;
    }

    private Object parseValue(tools.jackson.databind.JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isInt() || node.isLong()) {
            return node.longValue();
        }
        if (node.isDouble() || node.isFloat()) {
            return node.doubleValue();
        }
        return node.asString();
    }

    // ─── JSON serialization helpers ─────────────────────────────────────────

    private ArrayNode toJsonArray(List<NeutralValue> values) {
        ArrayNode array = json.createArrayNode();
        for (NeutralValue v : values) {
            ObjectNode node = json.createObjectNode();
            node.put("nodeId", v.nodeId());
            node.put("sourceTime", v.sourceTime().toString());
            if (v.value() == null) {
                node.putNull("value");
            } else if (v.value() instanceof Boolean b) {
                node.put("value", b);
            } else if (v.value() instanceof Number n) {
                node.put("value", n.doubleValue());
            } else {
                node.put("value", v.value().toString());
            }
            node.put("quality", v.quality().name());
            if (v.qualityReason() != null) {
                node.put("qualityReason", v.qualityReason());
            } else {
                node.putNull("qualityReason");
            }
            array.add(node);
        }
        return array;
    }

    private static void putEntry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private static void validateFormatVersion(String version) {
        if (version == null) {
            throw new IllegalArgumentException("import ZIP manifest is missing formatVersion");
        }
        String[] parts = version.split("\\.");
        String[] supported = FORMAT_VERSION.split("\\.");
        if (parts.length < 2 || !parts[0].equals(supported[0])) {
            throw new IllegalArgumentException(
                    "unsupported recording export format version: " + version
                    + " (supported major: " + supported[0] + ")");
        }
    }

    private void requireProject(String projectId) {
        if (projects.findById(projectId).isEmpty()) {
            throw new ResourceNotFoundException("Project", projectId);
        }
    }

    private RecordingRow requireRecording(String projectId, String recordingId) {
        return recordings.findById(recordingId)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Recording", recordingId));
    }

    private static java.time.OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(java.time.ZoneOffset.UTC);
    }

    private Recording map(RecordingRow r) {
        return new Recording(
                r.id(), r.projectId(), r.dataSourceId(), r.schemaVersion(), r.origin(),
                r.scanType(), r.name(), r.valueCount(), r.createdAt().toInstant(), r.createdBy(), r.version());
    }
}
