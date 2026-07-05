package com.ainclusive.iotsim.api.recording;

import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.recording.RecordingService;
import com.ainclusive.iotsim.domain.recording.RecordingService.RecordingValue;
import com.ainclusive.iotsim.domain.recording.RecordingService.RecordingValuesPage;
import com.ainclusive.iotsim.protocolmodel.Quality;
import com.ainclusive.iotsim.protocolmodel.ValueFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Arrays;
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
@Tag(name = "Recordings")
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
                    + " Default limit 200, max 1000."
                    + " Optional filters: search (LIKE on path/nodeId), quality (comma-separated"
                    + " GOOD/UNCERTAIN/BAD), from and to (ISO-8601 instants).")
    @GetMapping
    @PreAuthorize(OBSERVE)
    public ValuesPageResponse list(
            @PathVariable String projectId,
            @PathVariable String recordingId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String quality,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        ValueFilter filter = buildFilter(search, quality, from, to);
        RecordingValuesPage page = recordings.listValues(projectId, recordingId, cursor, limit, filter);
        List<ValueResponse> items = page.items().stream().map(ValueResponse::from).toList();
        return new ValuesPageResponse(items, page.nextCursor(), page.total());
    }

    private static ValueFilter buildFilter(String search, String quality, Instant from, Instant to) {
        List<Quality> qualities = List.of();
        if (quality != null && !quality.isBlank()) {
            qualities = Arrays.stream(quality.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try {
                            return Quality.valueOf(s.toUpperCase());
                        } catch (IllegalArgumentException e) {
                            throw new org.springframework.web.server.ResponseStatusException(
                                    org.springframework.http.HttpStatus.BAD_REQUEST,
                                    "Unknown quality value '" + s + "'; expected GOOD, UNCERTAIN, or BAD");
                        }
                    })
                    .toList();
        }
        return new ValueFilter(search, qualities, from, to);
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
