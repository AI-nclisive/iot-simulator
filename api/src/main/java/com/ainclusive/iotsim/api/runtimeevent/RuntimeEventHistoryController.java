package com.ainclusive.iotsim.api.runtimeevent;

import com.ainclusive.iotsim.domain.runtimeevent.RuntimeEventHistoryPage;
import com.ainclusive.iotsim.domain.runtimeevent.RuntimeEventHistoryRequest;
import com.ainclusive.iotsim.domain.runtimeevent.RuntimeEventHistoryService;
import com.ainclusive.iotsim.domain.runtimeevent.RuntimeEventView;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Runtime-event history (IS-055): the point-in-time, filtered, paginated query
 * over the {@code runtime_events} log that backs SPEC "Observe Runtime Event
 * History". Live events are on the SSE runtime stream ({@code .../stream/runtime});
 * this is the history. Filters are project-scoped with optional source/run/type and
 * a {@code [from, to)} time window; paging is keyset via the opaque {@code cursor}.
 * Distinct from the user-activity audit stream. See backend-specs/05_API_CONTRACT.md.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class RuntimeEventHistoryController {

    private final RuntimeEventHistoryService history;
    private final ObjectMapper json;

    public RuntimeEventHistoryController(RuntimeEventHistoryService history, ObjectMapper json) {
        this.history = history;
        this.json = json;
    }

    @GetMapping("/{projectId}/runtime-events")
    public RuntimeEventHistoryResponse runtimeEvents(
            @PathVariable String projectId,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String run,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        RuntimeEventHistoryPage page = history.history(new RuntimeEventHistoryRequest(
                projectId, source, run, type, from, to, cursor, limit));
        List<RuntimeEventDto> events = page.events().stream().map(this::toDto).toList();
        return new RuntimeEventHistoryResponse(events, page.nextCursor());
    }

    private RuntimeEventDto toDto(RuntimeEventView view) {
        return new RuntimeEventDto(view.id(), view.type(), view.at(),
                view.dataSourceId(), view.runId(), payload(view.payloadJson()));
    }

    private JsonNode payload(String payloadJson) {
        return json.readTree(payloadJson != null ? payloadJson : "{}");
    }

    /** One runtime event; {@code payload} is the event document as a nested JSON object. */
    public record RuntimeEventDto(
            long id, String type, Instant at, String dataSourceId, String runId, JsonNode payload) {}

    /** A page of history, newest first, with an opaque {@code nextCursor} ({@code null} on the last page). */
    public record RuntimeEventHistoryResponse(List<RuntimeEventDto> events, String nextCursor) {}
}
