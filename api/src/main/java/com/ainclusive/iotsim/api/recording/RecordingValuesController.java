package com.ainclusive.iotsim.api.recording;

import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.recording.RecordingService;
import com.ainclusive.iotsim.domain.recording.RecordingService.RecordingValue;
import com.ainclusive.iotsim.domain.recording.RecordingService.RecordingValuesPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Paginated browse of captured values for a recording (IS-134).
 *
 * <p>Authorization: {@link Permission#OBSERVE} — same as {@code GET /recordings/{id}}.
 */
@RestController
@Tag(name = "Recording Values", description = "Browse captured values for a recording.")
@RequestMapping("/api/v1/projects/{projectId}/recordings/{recordingId}/values")
public class RecordingValuesController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";

    private final RecordingService recordings;

    public RecordingValuesController(RecordingService recordings) {
        this.recordings = recordings;
    }

    @Operation(
            summary = "Browse recording values",
            description =
                    "Returns captured values for a recording using cursor-based pagination."
                    + " Pass the returned nextCursor as the cursor parameter on the next call."
                    + " Default limit 200, max 1000.")
    @GetMapping
    @PreAuthorize(OBSERVE)
    public ValuesPageResponse list(
            @PathVariable String projectId,
            @PathVariable String recordingId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        RecordingValuesPage page = recordings.listValues(projectId, recordingId, cursor, limit);
        List<ValueResponse> items = page.items().stream().map(ValueResponse::from).toList();
        return new ValuesPageResponse(items, page.nextCursor(), page.total());
    }

    public record ValuesPageResponse(List<ValueResponse> items, String nextCursor, long total) {}

    public record ValueResponse(
            String parameterId,
            String parameterPath,
            Instant timestamp,
            String value,
            String quality) {

        static ValueResponse from(RecordingValue v) {
            return new ValueResponse(
                    v.parameterId(), v.parameterPath(), v.timestamp(), v.value(), v.quality());
        }
    }
}
