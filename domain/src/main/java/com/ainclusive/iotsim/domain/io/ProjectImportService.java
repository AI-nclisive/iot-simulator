package com.ainclusive.iotsim.domain.io;

import com.ainclusive.iotsim.domain.project.Project;
import com.ainclusive.iotsim.domain.project.ProjectService;
import com.ainclusive.iotsim.domain.sample.SampleService;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.recording.RecordingRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Imports a project from a versioned ZIP+manifest bundle (IS-073,
 * backend-specs/06_ARTIFACT_FORMATS.md).
 *
 * <p>Only {@code formatVersion} major == 1 is supported; a newer or incompatible
 * version is rejected with {@link ProjectImportException} (IS-091 compat guard).
 *
 * <p>Secrets are never present in the export format; the import creates data
 * sources with {@code ANONYMOUS} credential state (user can supply credentials
 * after import).
 *
 * <p>Safety guards:
 * <ul>
 *   <li>Per-entry size cap ({@link #MAX_ENTRY_BYTES}) prevents ZIP-bomb / heap exhaustion.</li>
 *   <li>Entry-count cap ({@link #MAX_ENTRIES}) limits the number of ZIP entries processed.</li>
 *   <li>Per-entry SHA-256 checksums declared in {@code manifest.entries} are verified
 *       against the actual bytes after reading (integrity check).</li>
 * </ul>
 */
@Service
public class ProjectImportService {

    private static final int SUPPORTED_MAJOR = 1;
    /** Maximum size of any single ZIP entry (10 MB). */
    private static final long MAX_ENTRY_BYTES = 10 * 1024 * 1024L;
    /** Maximum number of ZIP entries to process. */
    private static final int MAX_ENTRIES = 64;

    private static final TypeReference<List<SchemaNode>> SCHEMA_NODE_LIST = new TypeReference<>() {};

    private final ProjectService projects;
    private final DataSourceRepository dataSources;
    private final SchemaRepository schemas;
    private final RecordingRepository recordings;
    private final SampleService sampleService;
    private final ObjectMapper json;

    public ProjectImportService(ProjectService projects, DataSourceRepository dataSources,
            SchemaRepository schemas, RecordingRepository recordings,
            SampleService sampleService, ObjectMapper json) {
        this.projects = projects;
        this.dataSources = dataSources;
        this.schemas = schemas;
        this.recordings = recordings;
        this.sampleService = sampleService;
        this.json = json;
    }

    /**
     * Reads a project export ZIP from {@code inputStream} and reconstitutes the
     * project in the database.
     *
     * <p>The imported project gets a new ID so it can coexist with the original.
     * Data-source IDs are also re-assigned; recording and sample associations are
     * re-pointed to the new IDs.
     *
     * @return the newly created {@link Project}
     * @throws ProjectImportException if the ZIP is malformed, checksum mismatch,
     *                                or uses an unsupported format version
     */
    @Transactional
    public Project importProject(InputStream inputStream, String actor) {
        Map<String, byte[]> rawEntries = readZipEntries(inputStream);

        // Convert raw bytes to strings for JSON parsing.
        Map<String, String> zipEntries = new HashMap<>();
        for (Map.Entry<String, byte[]> e : rawEntries.entrySet()) {
            zipEntries.put(e.getKey(), new String(e.getValue(), StandardCharsets.UTF_8));
        }

        // --- manifest validation ---
        String manifestJson = zipEntries.get("manifest.json");
        if (manifestJson == null) {
            throw new ProjectImportException("Invalid project export: missing manifest.json");
        }
        JsonNode manifest = parseJson(manifestJson);
        validateFormatVersion(manifest.path("formatVersion").asString(null));
        verifyChecksums(manifest, rawEntries);

        // --- project ---
        String projectJson = zipEntries.get("project.json");
        if (projectJson == null) {
            throw new ProjectImportException("Invalid project export: missing project.json");
        }
        JsonNode projectNode = parseJson(projectJson);
        String name = projectNode.path("name").asString("Imported Project");
        String description = projectNode.path("description").asString(null);

        Project newProject = projects.create(name, description, actor);

        // --- data sources ---
        String dsJson = zipEntries.get("data-sources.json");
        Map<String, String> dsIdMap = new HashMap<>();  // old -> new
        if (dsJson != null) {
            JsonNode dsArray = parseJson(dsJson);
            if (dsArray.isArray()) {
                for (JsonNode ds : dsArray) {
                    String oldId = ds.path("id").asString(null);
                    String dsName = ds.path("name").asString("Imported Source");
                    String protocol = ds.path("protocol").asString("OPC_UA");
                    String basis = ds.path("basis").asString("IMPORT");
                    String endpoint = ds.path("endpoint").asString(null);
                    String runtimeConfig = ds.path("runtimeConfig").asString(null);
                    boolean enabled = ds.path("enabled").asBoolean(false);

                    DataSourceRow row = dataSources.insert(
                            newProject.id(), dsName, protocol, basis, endpoint, runtimeConfig, actor);

                    // Preserve the exported enabled state: insert() defaults to false;
                    // apply an update only when the exported value is true.
                    if (enabled) {
                        dataSources.update(row.id(), row.name(), row.endpoint(),
                                row.runtimeConfig(), true, row.version());
                    }

                    if (oldId != null) {
                        dsIdMap.put(oldId, row.id());
                    }
                }
            }
        }

        // --- schemas ---
        String schemasJson = zipEntries.get("schemas.json");
        if (schemasJson != null) {
            JsonNode schemasArray = parseJson(schemasJson);
            if (schemasArray.isArray()) {
                for (JsonNode schemaEntry : schemasArray) {
                    String oldDsId = schemaEntry.path("dataSourceId").asString(null);
                    String newDsId = dsIdMap.get(oldDsId);
                    if (newDsId == null) {
                        continue; // skip orphaned schema
                    }
                    JsonNode nodesNode = schemaEntry.path("nodes");
                    if (!nodesNode.isMissingNode() && nodesNode.isArray()) {
                        List<SchemaNode> nodes = json.treeToValue(nodesNode, SCHEMA_NODE_LIST);
                        if (!nodes.isEmpty()) {
                            schemas.saveNewVersion(newDsId, nodes);
                        }
                    }
                }
            }
        }

        // --- recordings ---
        String recordingsJson = zipEntries.get("recordings.json");
        Map<String, String> recIdMap = new HashMap<>();  // old -> new
        if (recordingsJson != null) {
            JsonNode recArray = parseJson(recordingsJson);
            if (recArray.isArray()) {
                for (JsonNode rec : recArray) {
                    String oldRecId = rec.path("id").asString(null);
                    String oldDsId = rec.path("dataSourceId").asString(null);
                    String newDsId = dsIdMap.get(oldDsId);
                    if (newDsId == null) {
                        continue; // skip recording referencing an unknown ds
                    }
                    int schemaVersion = rec.path("schemaVersion").asInt(0);
                    String origin = rec.path("origin").asString("IMPORT");
                    RecordingRow row = recordings.create(newProject.id(), newDsId, schemaVersion, origin, actor);
                    if (oldRecId != null) {
                        recIdMap.put(oldRecId, row.id());
                    }
                }
            }
        }

        // --- samples ---
        String samplesJson = zipEntries.get("samples.json");
        if (samplesJson != null) {
            JsonNode samplesArray = parseJson(samplesJson);
            if (samplesArray.isArray()) {
                for (JsonNode sample : samplesArray) {
                    String sampleName = sample.path("name").asString("Imported Sample");
                    String oldRecId = sample.path("derivedFromRecordingId").asString(null);
                    String newRecId = oldRecId != null ? recIdMap.get(oldRecId) : null;
                    String selection = sample.path("selection").asString("{}");
                    List<String> tags = new ArrayList<>();
                    JsonNode tagsNode = sample.path("tags");
                    if (tagsNode.isArray()) {
                        for (JsonNode tag : tagsNode) {
                            tags.add(tag.asString());
                        }
                    }
                    sampleService.create(newProject.id(), newRecId, sampleName, selection, tags, actor);
                }
            }
        }

        return newProject;
    }

    // -------------------------------------------------------------------------

    private void validateFormatVersion(String formatVersion) {
        if (formatVersion == null || formatVersion.isBlank()) {
            throw new ProjectImportException(
                    "Invalid project export: manifest is missing formatVersion");
        }
        String[] parts = formatVersion.split("\\.", 3);
        int major;
        try {
            major = Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            throw new ProjectImportException(
                    "Invalid project export: unrecognised formatVersion: " + formatVersion);
        }
        if (major != SUPPORTED_MAJOR) {
            throw new ProjectImportException(
                    "Unsupported project export version " + formatVersion
                    + " (supported major: " + SUPPORTED_MAJOR + ")");
        }
    }

    /**
     * Verifies per-entry SHA-256 checksums declared in {@code manifest.entries}
     * against the bytes actually read from the ZIP. Throws
     * {@link ProjectImportException} if any entry is missing or has a mismatched
     * checksum.
     */
    private void verifyChecksums(JsonNode manifest, Map<String, byte[]> rawEntries) {
        JsonNode entries = manifest.path("entries");
        if (entries.isMissingNode() || !entries.isArray()) {
            return; // no entries declared; skip verification (older compatible minor)
        }
        for (JsonNode entry : entries) {
            String path = entry.path("path").asString(null);
            String expectedSha = entry.path("sha256").asString(null);
            if (path == null || expectedSha == null) {
                continue;
            }
            byte[] bytes = rawEntries.get(path);
            if (bytes == null) {
                throw new ProjectImportException(
                        "Integrity check failed: manifest references missing entry: " + path);
            }
            String actualSha = sha256(bytes);
            if (!actualSha.equals(expectedSha)) {
                throw new ProjectImportException(
                        "Integrity check failed: SHA-256 mismatch for entry: " + path);
            }
        }
    }

    /**
     * Reads all ZIP entries into memory with size and count guards to prevent
     * ZIP-bomb / heap-exhaustion attacks.
     */
    private Map<String, byte[]> readZipEntries(InputStream in) {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entries.size() >= MAX_ENTRIES) {
                    throw new ProjectImportException(
                            "Invalid project export: too many ZIP entries (max " + MAX_ENTRIES + ")");
                }
                String name = entry.getName();
                byte[] bytes = readEntryBytes(zis, name);
                entries.put(name, bytes);
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new ProjectImportException("Failed to read project export ZIP", e);
        }
        return entries;
    }

    /**
     * Reads bytes from the current ZIP entry with a hard size cap to prevent
     * heap exhaustion from oversized or crafted entries.
     */
    private static byte[] readEntryBytes(ZipInputStream zis, String entryName) throws IOException {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int read;
        while ((read = zis.read(buffer)) != -1) {
            if (out.size() + read > MAX_ENTRY_BYTES) {
                throw new ProjectImportException(
                        "Invalid project export: entry too large (max " + MAX_ENTRY_BYTES + " bytes): "
                        + entryName);
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private JsonNode parseJson(String text) {
        try {
            return json.readTree(text);
        } catch (RuntimeException e) {
            throw new ProjectImportException("Failed to parse JSON in project export: " + e.getMessage(), e);
        }
    }
}
