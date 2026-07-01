package com.ainclusive.iotsim.api.sample;

import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.sample.Sample;
import com.ainclusive.iotsim.domain.sample.SampleService;
import com.ainclusive.iotsim.domain.support.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Named sample subsets/snapshots within a project (backend-specs/05_API_CONTRACT.md).
 *
 * <p>Authorization (IS-077): list/get — {@link Permission#OBSERVE};
 * create/delete — {@link Permission#SOURCE_EDIT} (admin: editing recording data).
 */
@RestController
@Tag(name = "Samples",
        description = "List, read, create, and delete reusable value samples used to seed source values and scenarios.")
@RequestMapping("/api/v1/projects/{projectId}/samples")
public class SampleController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";
    private static final String SOURCE_EDIT =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).SOURCE_EDIT)";

    private final SampleService sampleService;

    public SampleController(SampleService sampleService) {
        this.sampleService = sampleService;
    }

    @Operation(summary = "List samples",
            description = "Returns a page of samples in the project using cursor-based pagination.")
    @GetMapping
    @PreAuthorize(OBSERVE)
    public Page<SampleResponse> list(
            @PathVariable String projectId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return sampleService.listPaged(projectId, cursor, limit).map(SampleResponse::from);
    }

    @Operation(summary = "Create a sample",
            description = "Creates a new sample in the project and returns 201 Created with a Location header and ETag.")
    @PostMapping
    @PreAuthorize(SOURCE_EDIT)
    public ResponseEntity<SampleResponse> create(
            @PathVariable String projectId, @RequestBody CreateSampleRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        Sample sample = sampleService.create(
                projectId, req.derivedFromRecordingId(), req.name(), req.selection(), req.tags(), "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/samples/" + sample.id()))
                .eTag(etag(sample.version()))
                .body(SampleResponse.from(sample));
    }

    @Operation(summary = "Get a sample",
            description = "Returns a single sample by id, with its current version as the ETag.")
    @GetMapping("/{id}")
    @PreAuthorize(OBSERVE)
    public ResponseEntity<SampleResponse> get(@PathVariable String projectId, @PathVariable String id) {
        Sample sample = sampleService.get(projectId, id);
        return ResponseEntity.ok().eTag(etag(sample.version())).body(SampleResponse.from(sample));
    }

    @Operation(summary = "Delete a sample",
            description = "Deletes the sample by id and returns 204 No Content.")
    @DeleteMapping("/{id}")
    @PreAuthorize(SOURCE_EDIT)
    public ResponseEntity<Void> delete(@PathVariable String projectId, @PathVariable String id) {
        sampleService.delete(projectId, id);
        return ResponseEntity.noContent().build();
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    public record CreateSampleRequest(
            String name,
            String derivedFromRecordingId,
            String selection,
            List<String> tags) {}

    public record SampleResponse(
            String id,
            String projectId,
            String derivedFromRecordingId,
            String name,
            String selection,
            List<String> tags,
            Instant createdAt,
            String createdBy,
            long version) {

        static SampleResponse from(Sample s) {
            return new SampleResponse(
                    s.id(), s.projectId(), s.derivedFromRecordingId(), s.name(),
                    s.selection(), s.tags(), s.createdAt(), s.createdBy(), s.version());
        }
    }
}
