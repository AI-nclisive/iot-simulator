package com.ainclusive.iotsim.api.activerun;

import com.ainclusive.iotsim.domain.activerun.ActiveRun;
import com.ainclusive.iotsim.domain.activerun.ActiveRunService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Dashboard overview: currently-active (RUNNING or QUEUED) runs for a project (IS-122).
 * See backend-specs/05_API_CONTRACT.md.
 *
 * <p>GET /api/v1/projects/{projectId}/active-runs → {@code ActiveRunsResponse}
 */
@RestController
@Tag(name = "Runs")
@RequestMapping("/api/v1/projects/{projectId}/active-runs")
public class ActiveRunController {

    private final ActiveRunService activeRunService;

    public ActiveRunController(ActiveRunService activeRunService) {
        this.activeRunService = activeRunService;
    }

    @Operation(summary = "List active runs",
            description = "Returns the currently active (RUNNING or QUEUED) runs for the project as a dashboard view.")
    @GetMapping
    public ActiveRunsResponse list(@PathVariable String projectId) {
        List<ActiveRunResponse> items = activeRunService.getActiveRuns(projectId)
                .stream()
                .map(ActiveRunResponse::from)
                .toList();
        return new ActiveRunsResponse(items);
    }

    /** Wire-shape for a single active run (matches the frontend ActiveRun type). */
    public record ActiveRunResponse(
            String id,
            String label,
            String processType,
            String runState,
            String startedAt,
            String initiator,
            String relatedSourceId,
            String relatedLabel) {

        static ActiveRunResponse from(ActiveRun r) {
            return new ActiveRunResponse(
                    r.id(),
                    r.label(),
                    r.processType(),
                    r.runState(),
                    r.startedAt() != null ? r.startedAt().toString() : null,
                    r.initiator(),
                    r.relatedSourceId(),
                    r.relatedLabel());
        }
    }

    /** Top-level wrapper so the front-end can extend the envelope (e.g. add pagination later). */
    public record ActiveRunsResponse(List<ActiveRunResponse> items) {}
}
