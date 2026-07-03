package com.ainclusive.iotsim.api.recording;

import com.ainclusive.iotsim.domain.recording.RecordingService;
import com.ainclusive.iotsim.domain.recording.RecordingService.RecordingSchema;
import com.ainclusive.iotsim.protocolmodel.SchemaNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only view of the schema captured for a recording (IS-137).
 * Returns the schema snapshot linked to the recording at capture time.
 */
@RestController
@Tag(name = "Recording Schema", description = "Read the schema captured for a recording.")
@RequestMapping("/api/v1/projects/{projectId}/recordings/{recordingId}/schema")
public class RecordingSchemaController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";

    private final RecordingService recordings;

    public RecordingSchemaController(RecordingService recordings) {
        this.recordings = recordings;
    }

    @Operation(
            summary = "Get recording schema",
            description =
                    "Returns the protocol-neutral schema captured for a recording,"
                    + " ordered by node path.")
    @GetMapping
    @PreAuthorize(OBSERVE)
    public RecordingSchemaResponse get(
            @PathVariable String projectId,
            @PathVariable String recordingId) {
        RecordingSchema schema = recordings.getRecordingSchema(projectId, recordingId);
        List<SchemaNodeResponse> nodes = schema.nodes().stream()
                .map(SchemaNodeResponse::from)
                .toList();
        return new RecordingSchemaResponse(nodes);
    }

    public record RecordingSchemaResponse(List<SchemaNodeResponse> nodes) {}

    public record SchemaNodeResponse(
            String nodeId,
            String parentId,
            String path,
            String name,
            String kind,
            String dataType,
            String valueRank,
            String access,
            String unit,
            String description) {

        static SchemaNodeResponse from(SchemaNode n) {
            return new SchemaNodeResponse(
                    n.nodeId(),
                    n.parentId(),
                    n.path(),
                    n.name(),
                    n.kind().name(),
                    n.dataType() == null ? null : n.dataType().name(),
                    n.valueRank() == null ? null : n.valueRank().name(),
                    n.access() == null ? null : n.access().name(),
                    n.unit(),
                    n.description());
        }
    }
}
