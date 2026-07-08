package com.ainclusive.iotsim.api.activityevent;

import com.ainclusive.iotsim.domain.activityevent.ActivityEventHistoryPage;
import com.ainclusive.iotsim.domain.activityevent.ActivityEventHistoryRequest;
import com.ainclusive.iotsim.domain.activityevent.ActivityEventService;
import com.ainclusive.iotsim.domain.activityevent.ActivityEventView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * User-activity audit history (IS-083). Two endpoints:
 * <ul>
 *   <li>{@code GET /api/v1/projects/{id}/activity} — project-scoped; requires OBSERVE.</li>
 *   <li>{@code GET /api/v1/admin/activity} — all projects; requires ADMIN_ACCESS.</li>
 * </ul>
 * Both support optional filters ({@code actor}, {@code action}, {@code objectType},
 * {@code from}, {@code to}) and keyset pagination via opaque {@code cursor}. Newest events first.
 */
@RestController
@Tag(name = "Activity")
public class ActivityEventController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";

    private static final String ADMIN_ACCESS =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).ADMIN_ACCESS)";

    private final ActivityEventService service;
    private final ObjectMapper json;

    public ActivityEventController(ActivityEventService service, ObjectMapper json) {
        this.service = service;
        this.json = json;
    }

    @Operation(summary = "List project activity",
            description = "Returns a page of user-activity events for the project, newest first."
                    + " Filterable by actor, action, and object type; cursor-paginated.")
    @GetMapping("/api/v1/projects/{projectId}/activity")
    @PreAuthorize(OBSERVE)
    public ActivityEventHistoryResponse projectActivity(
            @PathVariable String projectId,
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        ActivityEventHistoryPage page = service.history(
                new ActivityEventHistoryRequest(projectId, actor, action, objectType, from, to, cursor, limit));
        return toResponse(page);
    }

    @Operation(summary = "List all activity (admin)",
            description = "Returns a page of user-activity events across all projects, newest first."
                    + " Admin-only. Supports the same filters and cursor paging as the project endpoint.")
    @GetMapping("/api/v1/admin/activity")
    @PreAuthorize(ADMIN_ACCESS)
    public ActivityEventHistoryResponse adminActivity(
            @RequestParam(required = false) String actor,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String objectType,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        ActivityEventHistoryPage page = service.history(
                new ActivityEventHistoryRequest(null, actor, action, objectType, from, to, cursor, limit));
        return toResponse(page);
    }

    private ActivityEventHistoryResponse toResponse(ActivityEventHistoryPage page) {
        List<ActivityEventDto> events = page.events().stream().map(this::toDto).toList();
        return new ActivityEventHistoryResponse(events, page.nextCursor());
    }

    private ActivityEventDto toDto(ActivityEventView view) {
        return new ActivityEventDto(
                view.id(), view.projectId(), view.actor(), view.action(),
                view.objectType(), view.objectId(), view.at(), detail(view.detailJson()));
    }

    private JsonNode detail(String detailJson) {
        return json.readTree(detailJson != null ? detailJson : "{}");
    }

    /** One activity event. {@code projectId} and {@code objectId} may be {@code null}. */
    public record ActivityEventDto(
            long id,
            String projectId,
            String actor,
            String action,
            String objectType,
            String objectId,
            Instant at,
            JsonNode detail) {}

    /** A page of history, newest first, with an opaque {@code nextCursor} ({@code null} on the last page). */
    public record ActivityEventHistoryResponse(List<ActivityEventDto> events, String nextCursor) {}
}
