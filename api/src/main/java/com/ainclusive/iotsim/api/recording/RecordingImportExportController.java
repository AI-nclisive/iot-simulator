package com.ainclusive.iotsim.api.recording;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.recording.Recording;
import com.ainclusive.iotsim.domain.recording.RecordingBundle;
import com.ainclusive.iotsim.domain.recording.RecordingImportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Recording import and export endpoints (IS-070, backend-specs/05 &amp; 06):
 *
 * <ul>
 *   <li>{@code POST /api/v1/projects/{projectId}/recordings/{id}/export} — build + stream a ZIP</li>
 *   <li>{@code GET  /api/v1/projects/{projectId}/recordings/{id}/download} — stream a cached ZIP</li>
 *   <li>{@code POST /api/v1/projects/{projectId}/recordings/import} — multipart ZIP upload</li>
 * </ul>
 *
 * <p>The export endpoint always re-builds the ZIP from the live timeline data and
 * stores it in the object store, then streams the result. The download endpoint
 * serves the previously-stored blob (404 before first export).
 */
@RestController
@Tag(
        name = "Recording Import/Export",
        description =
                "Export a recording as a ZIP, download a previously exported ZIP,"
                + " and import a recording ZIP.")
@RequestMapping("/api/v1/projects/{projectId}/recordings")
public class RecordingImportExportController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";
    private static final String IMPORT_EXPORT =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).IMPORT_EXPORT)";

    private final RecordingImportExportService service;

    public RecordingImportExportController(RecordingImportExportService service) {
        this.service = service;
    }

    /**
     * Builds and streams a recording export ZIP (re-builds on every call so the
     * export always reflects the current timeline state).
     */
    @Operation(
            summary = "Export a recording",
            description =
                    "Rebuilds the export ZIP from the current timeline data, stores it in the object"
                    + " store, then streams it as an attachment. Re-builds on every call.")
    @PostMapping("/{id}/export")
    @PreAuthorize(IMPORT_EXPORT)
    public ResponseEntity<InputStreamResource> export(
            @PathVariable String projectId, @PathVariable String id) {
        RecordingBundle bundle = service.export(projectId, id);
        return streamBundle(bundle);
    }

    /**
     * Serves a previously-built export ZIP from the object store; 404 if not yet exported.
     */
    @Operation(
            summary = "Download exported ZIP",
            description =
                    "Streams the previously stored export ZIP from the object store."
                    + " Returns 404 if the recording has not been exported yet.")
    @GetMapping("/{id}/download")
    @PreAuthorize(OBSERVE)
    public ResponseEntity<InputStreamResource> download(
            @PathVariable String projectId, @PathVariable String id) {
        RecordingBundle bundle = service.openBundle(projectId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Recording export bundle", id));
        return streamBundle(bundle);
    }

    /**
     * Imports a recording from a multipart ZIP upload.
     * Returns 201 with the new recording resource.
     */
    @Operation(
            summary = "Import a recording",
            description =
                    "Imports a recording from a multipart ZIP upload (the file part) and"
                    + " returns 201 with the new recording resource.")
    @PostMapping("/import")
    @PreAuthorize(IMPORT_EXPORT)
    public ResponseEntity<RecordingResponse> importRecording(
            @PathVariable String projectId,
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        byte[] zipContent = file.getBytes();
        Recording recording = service.importRecording(projectId, zipContent, "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/recordings/" + recording.id()))
                .body(RecordingResponse.from(recording));
    }

    private static ResponseEntity<InputStreamResource> streamBundle(RecordingBundle bundle) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(bundle.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + bundle.filename() + "\"")
                .body(new InputStreamResource(bundle.content()));
    }

    /** Response shape mirrors {@link RecordingController.RecordingResponse}. */
    public record RecordingResponse(
            String id, String projectId, String dataSourceId, int schemaVersion, String origin,
            long valueCount, Instant createdAt, String createdBy, long version) {

        static RecordingResponse from(Recording r) {
            return new RecordingResponse(
                    r.id(), r.projectId(), r.dataSourceId(), r.schemaVersion(), r.origin(),
                    r.valueCount(), r.createdAt(), r.createdBy(), r.version());
        }
    }
}
