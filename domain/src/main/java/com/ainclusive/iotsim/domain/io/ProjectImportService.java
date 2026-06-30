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
import java.util.ArrayList;
import java.util.HashMap;
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
 */
@Service
public class ProjectImportService {

    private static final int SUPPORTED_MAJOR = 1;
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
     * @throws ProjectImportException if the ZIP is malformed or uses an unsupported version
     */
    @Transactional
    public Project importProject(InputStream inputStream, String actor) {
        Map<String, String> zipEntries = readZipEntries(inputStream);

        // --- manifest validation ---
        String manifestJson = zipEntries.get("manifest.json");
        if (manifestJson == null) {
            throw new ProjectImportException("Invalid project export: missing manifest.json");
        }
        JsonNode manifest = parseJson(manifestJson);
        validateFormatVersion(manifest.path("formatVersion").asString(null));

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

    private Map<String, String> readZipEntries(InputStream in) {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(in)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();
                byte[] bytes = zis.readAllBytes();
                entries.put(name, new String(bytes, StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new ProjectImportException("Failed to read project export ZIP", e);
        }
        return entries;
    }

    private JsonNode parseJson(String text) {
        try {
            return json.readTree(text);
        } catch (RuntimeException e) {
            throw new ProjectImportException("Failed to parse JSON in project export: " + e.getMessage(), e);
        }
    }
}
