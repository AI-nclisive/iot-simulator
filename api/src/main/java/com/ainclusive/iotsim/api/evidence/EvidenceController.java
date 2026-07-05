package com.ainclusive.iotsim.api.evidence;

import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.evidence.EvidenceBundle;
import com.ainclusive.iotsim.domain.evidence.EvidenceFormat;
import com.ainclusive.iotsim.domain.evidence.EvidenceService;
import com.ainclusive.iotsim.domain.evidence.EvidenceView;
import com.ainclusive.iotsim.domain.support.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Evidence resource (IS-057, SPEC "Export Run Evidence"): list/get run evidence,
 * trigger an export, and download the produced bundle. Export is idempotent-ish —
 * re-{@code POST}ing after an {@code EXPORT_FAILED} retries (backend-specs/05).
 *
 * <p>Authorization (IS-077): list/get/download — {@link Permission#OBSERVE} (user + admin);
 * export trigger — {@link Permission#IMPORT_EXPORT} (admin).
 */
@RestController
@Tag(name = "Evidence")
@RequestMapping("/api/v1/projects/{projectId}/evidence")
public class EvidenceController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";
    private static final String IMPORT_EXPORT =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).IMPORT_EXPORT)";

    private final EvidenceService evidence;
    private final ObjectMapper json;

    public EvidenceController(EvidenceService evidence, ObjectMapper json) {
        this.evidence = evidence;
        this.json = json;
    }

    @Operation(
            summary = "List evidence",
            description =
                    "Returns run evidence in the project using cursor-based pagination"
                    + " (cursor and limit query parameters).")
    @GetMapping
    @PreAuthorize(OBSERVE)
    public Page<EvidenceResponse> list(
            @PathVariable String projectId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return evidence.listPaged(projectId, cursor, limit).map(this::toResponse);
    }

    @Operation(
            summary = "Get evidence",
            description =
                    "Returns a single evidence record by id, including its content manifest.")
    @GetMapping("/{id}")
    @PreAuthorize(OBSERVE)
    public EvidenceResponse get(@PathVariable String projectId, @PathVariable String id) {
        return evidence.find(projectId, id).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence", id));
    }

    @Operation(
            summary = "Export evidence",
            description =
                    "Triggers export of the evidence in the given format (defaults to BUNDLE)."
                    + " Idempotent-ish: re-posting after a failed export retries it.")
    @PostMapping("/{id}/export")
    @PreAuthorize(IMPORT_EXPORT)
    public EvidenceResponse export(@PathVariable String projectId, @PathVariable String id,
            @RequestParam(defaultValue = "BUNDLE") EvidenceFormat format) {
        return toResponse(evidence.export(projectId, id, format));
    }

    @Operation(
            summary = "Download evidence bundle",
            description =
                    "Streams the produced evidence bundle as an attachment."
                    + " Returns 404 if no bundle has been exported yet.")
    @GetMapping("/{id}/download")
    @PreAuthorize(OBSERVE)
    public ResponseEntity<InputStreamResource> download(
            @PathVariable String projectId, @PathVariable String id) {
        EvidenceBundle bundle = evidence.openBundle(projectId, id)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence bundle", id));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(bundle.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + bundle.filename() + "\"")
                .body(new InputStreamResource(bundle.content()));
    }

    private EvidenceResponse toResponse(EvidenceView view) {
        return new EvidenceResponse(view.id(), view.runId(), view.status(),
                manifest(view.manifestJson()), view.createdAt(), view.createdBy(), view.objectRef() != null);
    }

    private JsonNode manifest(String manifestJson) {
        return json.readTree(manifestJson != null ? manifestJson : "{}");
    }

    /** Evidence metadata; {@code manifest} is the content manifest as a nested object. */
    public record EvidenceResponse(
            String id, String runId, String status, JsonNode manifest,
            Instant createdAt, String createdBy, boolean exported) {}
}
