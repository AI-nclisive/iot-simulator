package com.ainclusive.iotsim.api.io;

import com.ainclusive.iotsim.domain.io.ProjectBundle;
import com.ainclusive.iotsim.domain.io.ProjectExportService;
import com.ainclusive.iotsim.domain.io.ProjectImportException;
import com.ainclusive.iotsim.domain.io.ProjectImportService;
import com.ainclusive.iotsim.domain.project.Project;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Project import/export endpoints (IS-073, backend-specs/05 & 06).
 *
 * <ul>
 *   <li>{@code POST /api/v1/projects/{id}/export} — exports the project as a
 *       versioned ZIP+manifest; returns the ZIP for download.</li>
 *   <li>{@code POST /api/v1/projects/import} — imports a project ZIP; returns the
 *       newly created project metadata.</li>
 * </ul>
 *
 * <p>The export is secret-free (credentials never serialised). The import creates
 * data sources with ANONYMOUS credential state; users supply credentials separately.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectIOController {

    private final ProjectExportService exportService;
    private final ProjectImportService importService;

    public ProjectIOController(ProjectExportService exportService,
            ProjectImportService importService) {
        this.exportService = exportService;
        this.importService = importService;
    }

    /**
     * Exports a project as a versioned ZIP+manifest bundle.
     *
     * <p>Returns the ZIP with {@code Content-Disposition: attachment} so clients
     * get a direct download. Responds 404 if the project does not exist.
     */
    @PostMapping("/{id}/export")
    public ResponseEntity<InputStreamResource> export(@PathVariable String id) {
        ProjectBundle bundle = exportService.export(id);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(bundle.filename())
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(bundle.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(new InputStreamResource(bundle.content()));
    }

    /**
     * Imports a project from a ZIP multipart upload.
     *
     * <p>Accepts {@code multipart/form-data} with a {@code file} part containing
     * the ZIP. Returns 201 Created with the newly created project representation.
     * Responds 422 if the ZIP is malformed or uses an unsupported format version.
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportedProjectResponse> importProject(
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ProjectImportException("No file provided for import");
        }
        try (InputStream in = file.getInputStream()) {
            Project project = importService.importProject(in, "local");
            ImportedProjectResponse response = ImportedProjectResponse.from(project);
            return ResponseEntity
                    .created(URI.create("/api/v1/projects/" + project.id()))
                    .body(response);
        } catch (IOException e) {
            throw new ProjectImportException("Failed to read uploaded file", e);
        }
    }

    /** Minimal project representation returned after a successful import. */
    public record ImportedProjectResponse(
            String id, String name, String description, String status,
            Instant createdAt, Instant updatedAt, String createdBy, long version) {

        static ImportedProjectResponse from(Project p) {
            return new ImportedProjectResponse(
                    p.id(), p.name(), p.description(), p.status().name(),
                    p.createdAt(), p.updatedAt(), p.createdBy(), p.version());
        }
    }
}
