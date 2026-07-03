package com.ainclusive.iotsim.api.recording;

import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.recording.Recording;
import com.ainclusive.iotsim.domain.recording.RecordingService;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.protocolmodel.ScanType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recordings within a project (backend-specs/05_API_CONTRACT.md).
 *
 * <p>Authorization (IS-077): list/get — {@link Permission#OBSERVE};
 * create (manual recording shell) — {@link Permission#SOURCE_EDIT} (admin-level: creating
 * a recording entity is a configuration action distinct from starting live capture).
 */
@RestController
@Tag(
        name = "Recordings",
        description =
                "List, read, and create recordings — captured timelines of real source values"
                + " that can be replayed.")
@RequestMapping("/api/v1/projects/{projectId}/recordings")
public class RecordingController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";
    private static final String SOURCE_EDIT =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).SOURCE_EDIT)";

    private final RecordingService recordings;

    public RecordingController(RecordingService recordings) {
        this.recordings = recordings;
    }

    @Operation(
            summary = "List recordings",
            description =
                    "Returns recordings in the project using cursor-based pagination"
                    + " (cursor and limit query parameters).")
    @GetMapping
    @PreAuthorize(OBSERVE)
    public Page<RecordingResponse> list(
            @PathVariable String projectId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return recordings.listPaged(projectId, cursor, limit).map(RecordingResponse::from);
    }

    @Operation(
            summary = "Create a recording shell",
            description =
                    "Creates an empty recording entity for the given data source and returns 201"
                    + " with a Location header. This is a configuration action, distinct from"
                    + " starting live capture.")
    @PostMapping
    @PreAuthorize(SOURCE_EDIT)
    public ResponseEntity<RecordingResponse> create(
            @PathVariable String projectId, @RequestBody CreateRecordingRequest req) {
        if (req == null || req.dataSourceId() == null || req.dataSourceId().isBlank()) {
            throw new IllegalArgumentException("dataSourceId is required");
        }
        ScanType scanType = req.scanType() != null ? req.scanType() : ScanType.SCHEMA_AND_DATA;
        Recording recording = recordings.create(projectId, req.dataSourceId(), scanType, "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/recordings/" + recording.id()))
                .eTag(etag(recording.version()))
                .body(RecordingResponse.from(recording));
    }

    @Operation(
            summary = "Get a recording",
            description =
                    "Returns a single recording by id, with its version as the ETag response header.")
    @GetMapping("/{id}")
    @PreAuthorize(OBSERVE)
    public ResponseEntity<RecordingResponse> get(@PathVariable String projectId, @PathVariable String id) {
        Recording recording = recordings.get(projectId, id);
        return ResponseEntity.ok().eTag(etag(recording.version())).body(RecordingResponse.from(recording));
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    public record CreateRecordingRequest(String dataSourceId, ScanType scanType) {}

    public record RecordingResponse(
            String id, String projectId, String dataSourceId, int schemaVersion, String origin,
            String scanType, long valueCount, Instant createdAt, String createdBy, long version) {

        static RecordingResponse from(Recording r) {
            return new RecordingResponse(
                    r.id(), r.projectId(), r.dataSourceId(), r.schemaVersion(), r.origin(),
                    r.scanType(), r.valueCount(), r.createdAt(), r.createdBy(), r.version());
        }
    }
}
