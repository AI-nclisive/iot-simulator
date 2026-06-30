package com.ainclusive.iotsim.api.sample;

import com.ainclusive.iotsim.domain.sample.Sample;
import com.ainclusive.iotsim.domain.sample.SampleService;
import com.ainclusive.iotsim.domain.support.Page;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Named sample subsets/snapshots within a project (backend-specs/05_API_CONTRACT.md). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/samples")
public class SampleController {

    private final SampleService sampleService;

    public SampleController(SampleService sampleService) {
        this.sampleService = sampleService;
    }

    @GetMapping
    public Page<SampleResponse> list(
            @PathVariable String projectId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return sampleService.listPaged(projectId, cursor, limit).map(SampleResponse::from);
    }

    @PostMapping
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

    @GetMapping("/{id}")
    public ResponseEntity<SampleResponse> get(@PathVariable String projectId, @PathVariable String id) {
        Sample sample = sampleService.get(projectId, id);
        return ResponseEntity.ok().eTag(etag(sample.version())).body(SampleResponse.from(sample));
    }

    @DeleteMapping("/{id}")
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
