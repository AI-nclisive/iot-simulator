package com.ainclusive.iotsim.domain.io;

import com.ainclusive.iotsim.domain.datasource.DataSource;
import com.ainclusive.iotsim.domain.project.Project;
import com.ainclusive.iotsim.domain.recording.Recording;
import com.ainclusive.iotsim.domain.sample.Sample;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Serializes {@link ProjectExportContent} into a versioned ZIP+manifest bundle
 * (IS-073, backend-specs/06_ARTIFACT_FORMATS.md).
 *
 * <p>ZIP layout:
 * <ul>
 *   <li>{@code manifest.json} — ProjectManifest with per-entry SHA-256 checksums</li>
 *   <li>{@code project.json} — project metadata (no secrets)</li>
 *   <li>{@code data-sources.json} — all data-source configs, including {@code securityConfig}
 *       (simulated-server endpoint auth with PBKDF2 password hashes); raw/plaintext connection
 *       secrets are excluded</li>
 *   <li>{@code schemas.json} — schema versions + nodes per data source</li>
 *   <li>{@code recordings.json} — recording metadata rows</li>
 *   <li>{@code samples.json} — sample metadata rows</li>
 * </ul>
 *
 * <p>Secrets exclusion contract: real-source connection secrets (tokens, plaintext passwords)
 * are session-only and never stored on {@code DataSource} — they remain excluded structurally.
 * The simulated-server accepted credentials in {@code securityConfig} are intentionally
 * exported as non-recoverable PBKDF2 hashes; they are part of the simulation configuration
 * (see backend-specs/08_AUTH_AND_MODES.md, IS-131) and contain no plaintext secrets.
 */
@Component
public class ProjectZipExporter {

    private final ObjectMapper json;

    public ProjectZipExporter(ObjectMapper json) {
        this.json = json;
    }

    /** Writes the full project export ZIP to {@code out} (stream stays open). */
    public void write(ProjectExportContent content, OutputStream out) {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            Map<String, byte[]> entries = buildEntries(content);
            List<ProjectManifest.ManifestEntry> manifestEntries = new ArrayList<>();
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                manifestEntries.add(new ProjectManifest.ManifestEntry(e.getKey(), sha256(e.getValue())));
            }

            ProjectManifest manifest = buildManifest(content.project(), manifestEntries);
            byte[] manifestBytes = json.writeValueAsBytes(manifest);

            entry(zip, "manifest.json", manifestBytes);
            for (Map.Entry<String, byte[]> e : entries.entrySet()) {
                entry(zip, e.getKey(), e.getValue());
            }
        } catch (IOException ex) {
            throw new UncheckedIOException("failed to write project export bundle", ex);
        }
    }

    /** Serializes and returns the ZIP bytes (convenience for tests and in-memory use). */
    public byte[] toBytes(ProjectExportContent content) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        write(content, buf);
        return buf.toByteArray();
    }

    /** Creates an {@link InputStream} over the serialized ZIP. */
    public InputStream toStream(ProjectExportContent content) {
        return new java.io.ByteArrayInputStream(toBytes(content));
    }

    // -------------------------------------------------------------------------

    /** Builds all data entries (everything except the manifest itself). */
    private Map<String, byte[]> buildEntries(ProjectExportContent content) {
        Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        entries.put("project.json", write(projectDto(content.project())));
        entries.put("data-sources.json", write(content.dataSources().stream()
                .map(ProjectZipExporter::dataSourceDto).toList()));
        entries.put("schemas.json", write(schemasDto(content.schemas())));
        entries.put("recordings.json", write(content.recordings().stream()
                .map(ProjectZipExporter::recordingDto).toList()));
        entries.put("samples.json", write(content.samples().stream()
                .map(ProjectZipExporter::sampleDto).toList()));
        return entries;
    }

    private byte[] write(Object value) {
        return json.writeValueAsBytes(value);
    }

    // --- DTOs (secret-free projections) --------------------------------------

    private static Map<String, Object> projectDto(Project p) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", p.id());
        m.put("name", p.name());
        m.put("description", p.description());
        m.put("status", p.status().name());
        m.put("createdAt", p.createdAt().toString());
        m.put("createdBy", p.createdBy());
        return m;
    }

    /**
     * Projects a data source to its exportable form.
     *
     * <p>Excluded: real-source connection secrets, tokens, and PLAINTEXT passwords.
     * The {@code credentialState} runtime enum is also excluded by name to make the contract
     * obvious. Included: {@code securityConfig} with PBKDF2 password hashes (non-recoverable,
     * never plaintext) — these are the simulated-server accepted credentials, part of the
     * simulation config (IS-131).
     */
    private static Map<String, Object> dataSourceDto(DataSource ds) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", ds.id());
        m.put("name", ds.name());
        m.put("protocol", ds.protocol().name());
        m.put("basis", ds.basis().name());
        m.put("simulatorPort", ds.simulatorPort());
        m.put("realDeviceEndpoint", ds.realDeviceEndpoint());
        m.put("runtimeConfig", ds.runtimeConfig());
        m.put("securityConfig", ds.securityConfig());  // hashed; part of the simulation (IS-131)
        m.put("enabled", ds.enabled());
        m.put("schemaVersion", ds.schemaVersion());
        m.put("createdAt", ds.createdAt().toString());
        m.put("createdBy", ds.createdBy());
        // Included: securityConfig (PBKDF2 hashes, non-recoverable, part of simulation).
        // NOT included: credentialState, runtimeState (runtime only), raw/plaintext connection credentials.
        return m;
    }

    private static List<Map<String, Object>> schemasDto(Map<String, SchemaWithNodes> schemas) {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Map.Entry<String, SchemaWithNodes> e : schemas.entrySet()) {
            SchemaWithNodes s = e.getValue();
            Map<String, Object> m = new HashMap<>();
            m.put("dataSourceId", e.getKey());
            m.put("schemaId", s.id());
            m.put("version", s.version());
            m.put("createdAt", s.createdAt().toString());
            m.put("nodes", s.nodes());
            list.add(m);
        }
        return list;
    }

    private static Map<String, Object> recordingDto(Recording r) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", r.id());
        m.put("dataSourceId", r.dataSourceId());
        m.put("schemaVersion", r.schemaVersion());
        m.put("origin", r.origin());
        m.put("scanType", r.scanType());
        m.put("valueCount", r.valueCount());
        m.put("createdAt", r.createdAt().toString());
        m.put("createdBy", r.createdBy());
        return m;
    }

    private static Map<String, Object> sampleDto(Sample s) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", s.id());
        m.put("derivedFromRecordingId", s.derivedFromRecordingId());
        m.put("name", s.name());
        m.put("selection", s.selection());
        m.put("tags", s.tags());
        m.put("createdAt", s.createdAt().toString());
        m.put("createdBy", s.createdBy());
        return m;
    }

    // --- manifest + checksum -------------------------------------------------

    private static ProjectManifest buildManifest(Project project,
            List<ProjectManifest.ManifestEntry> entries) {
        ProjectManifest.ProjectInfo info = new ProjectManifest.ProjectInfo(
                project.id(), project.name(), project.description(),
                project.status().name(), project.createdAt(), project.createdBy());
        return new ProjectManifest(
                ProjectManifest.FORMAT_VERSION,
                Instant.now(),
                "1.0.0",
                info,
                entries);
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static void entry(ZipOutputStream zip, String name, byte[] bytes) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        zip.write(bytes);
        zip.closeEntry();
    }
}
