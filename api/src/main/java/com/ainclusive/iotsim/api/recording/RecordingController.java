package com.ainclusive.iotsim.api.recording;

import com.ainclusive.iotsim.domain.recording.Recording;
import com.ainclusive.iotsim.domain.recording.RecordingService;
import com.ainclusive.iotsim.domain.support.Page;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Recordings within a project (backend-specs/05_API_CONTRACT.md). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/recordings")
public class RecordingController {

    private final RecordingService recordings;

    public RecordingController(RecordingService recordings) {
        this.recordings = recordings;
    }

    @GetMapping
    public Page<RecordingResponse> list(
            @PathVariable String projectId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return recordings.listPaged(projectId, cursor, limit).map(RecordingResponse::from);
    }

    @PostMapping
    public ResponseEntity<RecordingResponse> create(
            @PathVariable String projectId, @RequestBody CreateRecordingRequest req) {
        if (req == null || req.dataSourceId() == null || req.dataSourceId().isBlank()) {
            throw new IllegalArgumentException("dataSourceId is required");
        }
        Recording recording = recordings.create(projectId, req.dataSourceId(), "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/recordings/" + recording.id()))
                .eTag(etag(recording.version()))
                .body(RecordingResponse.from(recording));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RecordingResponse> get(@PathVariable String projectId, @PathVariable String id) {
        Recording recording = recordings.get(projectId, id);
        return ResponseEntity.ok().eTag(etag(recording.version())).body(RecordingResponse.from(recording));
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    public record CreateRecordingRequest(String dataSourceId) {}

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
