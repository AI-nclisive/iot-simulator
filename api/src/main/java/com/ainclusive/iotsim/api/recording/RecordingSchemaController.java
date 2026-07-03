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

/** Exposes the schema captured for a recording (IS-137). */
@RestController
@Tag(name = "Recording Schema", description = "Schema captured for a recording.")
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
            description = "Returns the schema nodes captured for a recording.")
    @GetMapping
    @PreAuthorize(OBSERVE)
    public RecordingSchemaResponse get(
            @PathVariable String projectId,
            @PathVariable String recordingId) {
        RecordingSchema schema = recordings.getRecordingSchema(projectId, recordingId);
        return new RecordingSchemaResponse(schema.nodes());
    }

    public record RecordingSchemaResponse(List<SchemaNode> nodes) {}
}
