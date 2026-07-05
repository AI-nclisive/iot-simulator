package com.ainclusive.iotsim.api.sample;

import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.sample.Sample;
import com.ainclusive.iotsim.domain.sample.SampleBundle;
import com.ainclusive.iotsim.domain.sample.SampleImportExportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.List;
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
 * Sample import and export endpoints (IS-070, backend-specs/05 &amp; 06):
 *
 * <ul>
 *   <li>{@code POST /api/v1/projects/{projectId}/samples/{id}/export} — build + stream a ZIP</li>
 *   <li>{@code GET  /api/v1/projects/{projectId}/samples/{id}/download} — stream a cached ZIP</li>
 *   <li>{@code POST /api/v1/projects/{projectId}/samples/import} — multipart ZIP upload</li>
 * </ul>
 *
 * <p>Authorization (IS-077): export/import — {@link Permission#IMPORT_EXPORT} (admin);
 * download — {@link Permission#OBSERVE} (user + admin).
 */
@RestController
@Tag(name = "Samples")
@RequestMapping("/api/v1/projects/{projectId}/samples")
public class SampleImportExportController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";
    private static final String IMPORT_EXPORT =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).IMPORT_EXPORT)";

    private final SampleImportExportService service;

    public SampleImportExportController(SampleImportExportService service) {
        this.service = service;
    }

    /**
     * Builds and streams a sample export ZIP.
     */
    @Operation(summary = "Export a sample as a ZIP",
            description = "Rebuilds and stores the sample bundle, then streams it back as an attachment ZIP.")
    @PostMapping("/{id}/export")
    @PreAuthorize(IMPORT_EXPORT)
    public ResponseEntity<InputStreamResource> export(
            @PathVariable String projectId, @PathVariable String id) {
        SampleBundle bundle = service.export(projectId, id);
        return streamBundle(bundle);
    }

    /**
     * Serves a previously-built export ZIP from the object store; 404 if not yet exported.
     */
    @Operation(summary = "Download a previously exported ZIP",
            description = "Streams the previously stored sample bundle ZIP; returns 404 before the first export.")
    @GetMapping("/{id}/download")
    @PreAuthorize(OBSERVE)
    public ResponseEntity<InputStreamResource> download(
            @PathVariable String projectId, @PathVariable String id) {
        SampleBundle bundle = service.openBundle(projectId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Sample export bundle", id));
        return streamBundle(bundle);
    }

    /**
     * Imports a sample from a multipart ZIP upload.
     * Returns 201 with the new sample resource.
     */
    @Operation(summary = "Import a sample ZIP",
            description = "Imports a sample from a multipart ZIP upload and returns 201 Created with the new sample.")
    @PostMapping("/import")
    @PreAuthorize(IMPORT_EXPORT)
    public ResponseEntity<SampleResponse> importSample(
            @PathVariable String projectId,
            @RequestParam("file") MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file is required");
        }
        byte[] zipContent = file.getBytes();
        Sample sample = service.importSample(projectId, zipContent, "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/samples/" + sample.id()))
                .body(SampleResponse.from(sample));
    }

    private static ResponseEntity<InputStreamResource> streamBundle(SampleBundle bundle) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(bundle.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + bundle.filename() + "\"")
                .body(new InputStreamResource(bundle.content()));
    }

    /** Response shape mirrors {@link SampleController.SampleResponse}. */
    public record SampleResponse(
            String id, String projectId, String derivedFromRecordingId, String name,
            String selection, List<String> tags, Instant createdAt, String createdBy, long version) {

        static SampleResponse from(Sample s) {
            return new SampleResponse(
                    s.id(), s.projectId(), s.derivedFromRecordingId(), s.name(),
                    s.selection(), s.tags(), s.createdAt(), s.createdBy(), s.version());
        }
    }
}
