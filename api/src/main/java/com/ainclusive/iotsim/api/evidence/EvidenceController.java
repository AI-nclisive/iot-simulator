package com.ainclusive.iotsim.api.evidence;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.evidence.EvidenceBundle;
import com.ainclusive.iotsim.domain.evidence.EvidenceFormat;
import com.ainclusive.iotsim.domain.evidence.EvidenceService;
import com.ainclusive.iotsim.domain.evidence.EvidenceView;
import com.ainclusive.iotsim.domain.support.Page;
import java.time.Instant;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/evidence")
public class EvidenceController {

    private final EvidenceService evidence;
    private final ObjectMapper json;

    public EvidenceController(EvidenceService evidence, ObjectMapper json) {
        this.evidence = evidence;
        this.json = json;
    }

    @GetMapping
    public Page<EvidenceResponse> list(
            @PathVariable String projectId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return evidence.listPaged(projectId, cursor, limit).map(this::toResponse);
    }

    @GetMapping("/{id}")
    public EvidenceResponse get(@PathVariable String projectId, @PathVariable String id) {
        return evidence.find(projectId, id).map(this::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Evidence", id));
    }

    @PostMapping("/{id}/export")
    public EvidenceResponse export(@PathVariable String projectId, @PathVariable String id,
            @RequestParam(defaultValue = "BUNDLE") EvidenceFormat format) {
        return toResponse(evidence.export(projectId, id, format));
    }

    @GetMapping("/{id}/download")
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
