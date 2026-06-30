package com.ainclusive.iotsim.domain.sample;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
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
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Export and import of sample artifacts as ZIP bundles (IS-070,
 * backend-specs/06_ARTIFACT_FORMATS.md §"Recording / sample export").
 *
 * <p>Sample export additionally records the {@code selection} field (node subset +
 * time window) alongside the value timeline. The manifest carries {@code formatVersion},
 * sample metadata, and the list of distinct node IDs in the exported values.
 *
 * <p>Import creates a new {@link Sample} row and, when the ZIP contains a
 * {@code value-timeline.json} entry, re-inserts the value timeline into the provided
 * recording context. Because samples are lightweight views, the timeline re-import is
 * optional: an import ZIP without timeline data produces a metadata-only sample.
 */
@Service
public class SampleImportExportService {

    static final String FORMAT_VERSION = SampleExportManifest.FORMAT_VERSION;

    private final SampleRepository samples;
    private final ValueTimelineRepository timeline;
    private final ProjectRepository projects;
    private final ObjectStore objectStore;
    private final ObjectMapper json;

    public SampleImportExportService(SampleRepository samples, ValueTimelineRepository timeline,
            ProjectRepository projects, ObjectStore objectStore, ObjectMapper json) {
        this.samples = samples;
        this.timeline = timeline;
        this.projects = projects;
        this.objectStore = objectStore;
        this.json = json;
    }

    // ─── Export ─────────────────────────────────────────────────────────────

    /**
     * Exports a sample to a ZIP bundle and stores it in the object store.
     * The value timeline is included only when the sample has a
     * {@code derivedFromRecordingId} (otherwise the timeline section is empty).
     *
     * @return a {@link SampleBundle} whose {@code content} stream the controller
     *         pipes directly to the HTTP response
     */
    public SampleBundle export(String projectId, String sampleId) {
        SampleRow row = requireSample(projectId, sampleId);

        List<NeutralValue> values = row.derivedFromRecordingId() != null
                ? timeline.readAll(row.derivedFromRecordingId())
                : List.of();

        List<String> nodeIds = values.stream().map(NeutralValue::nodeId).distinct().toList();
        List<String> tagList = parseTags(row.tags());

        SampleExportManifest manifest = new SampleExportManifest(
                FORMAT_VERSION,
                row.id(),
                row.projectId(),
                row.derivedFromRecordingId(),
                row.name(),
                row.selection(),
                tagList,
                Instant.now(),
                values.size(),
                nodeIds);

        byte[] zipBytes = buildZip(manifest, values);
        String key = "samples/" + sampleId + "/export.zip";
        objectStore.put(key, new ByteArrayInputStream(zipBytes), zipBytes.length, "application/zip");

        String filename = "sample-" + sampleId + ".zip";
        return new SampleBundle(new ByteArrayInputStream(zipBytes), "application/zip", filename);
    }

    /**
     * Returns a previously-exported bundle from the object store, or empty if not yet exported.
     */
    public Optional<SampleBundle> openBundle(String projectId, String sampleId) {
        requireSample(projectId, sampleId);
        String key = "samples/" + sampleId + "/export.zip";
        return objectStore.get(key).map(in ->
                new SampleBundle(in, "application/zip", "sample-" + sampleId + ".zip"));
    }

    // ─── Import ─────────────────────────────────────────────────────────────

    /**
     * Imports a sample from a ZIP bundle.
     *
     * @param projectId  target project (must exist)
     * @param zipContent raw bytes of the export ZIP
     * @param actor      user performing the import
     * @return the newly-created {@link Sample}
     */
    public Sample importSample(String projectId, byte[] zipContent, String actor) {
        requireProject(projectId);

        ImportPayload payload = parseZip(zipContent);
        SampleExportManifest manifest = payload.manifest();
        validateFormatVersion(manifest.formatVersion());

        String tagsJson = json.writeValueAsString(manifest.tags() != null ? manifest.tags() : List.of());
        String selectionJson = manifest.selection() != null ? manifest.selection() : "{}";

        SampleRow row = samples.create(
                projectId,
                manifest.derivedFromRecordingId(),
                manifest.name(),
                selectionJson,
                tagsJson,
                actor);

        return map(row);
    }

    // ─── ZIP build & parse ──────────────────────────────────────────────────

    private byte[] buildZip(SampleExportManifest manifest, List<NeutralValue> values) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(buf)) {
            putEntry(zip, "manifest.json", json.writeValueAsBytes(manifest));
            putEntry(zip, "value-timeline.json", json.writeValueAsBytes(toJsonArray(values)));
        } catch (IOException e) {
            throw new UncheckedIOException("failed to build sample export ZIP", e);
        }
        return buf.toByteArray();
    }

    private record ImportPayload(SampleExportManifest manifest, List<NeutralValue> values) {}

    /** Maximum decompressed size per ZIP entry (100 MiB). Prevents ZIP-bomb attacks. */
    private static final long MAX_ENTRY_BYTES = 100L * 1024 * 1024;

    private ImportPayload parseZip(byte[] zipContent) {
        SampleExportManifest manifest = null;
        List<NeutralValue> values = null;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(zipContent))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                byte[] bytes = readBounded(zip, entry.getName());
                if ("manifest.json".equals(entry.getName())) {
                    manifest = json.readValue(bytes, SampleExportManifest.class);
                } else if ("value-timeline.json".equals(entry.getName())) {
                    values = parseValues(bytes);
                }
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("failed to read sample import ZIP: " + e.getMessage(), e);
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

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {};

    private List<String> parseTags(String tagsJson) {
        if (tagsJson == null || tagsJson.isBlank()) {
            return List.of();
        }
        return json.readValue(tagsJson, STRING_LIST);
    }

    private static void validateFormatVersion(String version) {
        if (version == null) {
            throw new IllegalArgumentException("import ZIP manifest is missing formatVersion");
        }
        String[] parts = version.split("\\.");
        String[] supported = FORMAT_VERSION.split("\\.");
        if (parts.length < 2 || !parts[0].equals(supported[0])) {
            throw new IllegalArgumentException(
                    "unsupported sample export format version: " + version
                    + " (supported major: " + supported[0] + ")");
        }
    }

    private void requireProject(String projectId) {
        if (projects.findById(projectId).isEmpty()) {
            throw new ResourceNotFoundException("Project", projectId);
        }
    }

    private SampleRow requireSample(String projectId, String sampleId) {
        return samples.findById(sampleId)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Sample", sampleId));
    }

    private Sample map(SampleRow r) {
        List<String> tags = json.readValue(r.tags() != null ? r.tags() : "[]", STRING_LIST);
        return new Sample(r.id(), r.projectId(), r.derivedFromRecordingId(), r.name(),
                r.selection(), tags, r.createdAt().toInstant(), r.createdBy(), r.version());
    }
}
