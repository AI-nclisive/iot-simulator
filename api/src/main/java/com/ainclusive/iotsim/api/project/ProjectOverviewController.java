package com.ainclusive.iotsim.api.project;

import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.project.ProjectOverview;
import com.ainclusive.iotsim.domain.project.ProjectOverviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Project overview aggregation (IS-054): per-project configured/running source
 * counts, reusable-artifact counts, and an attention count of unhealthy sources.
 * Read-only derived view — no ETag/concurrency. The literal {@code /overview}
 * segment is matched ahead of {@code ProjectController}'s {@code /{id}} pattern.
 * See backend-specs/05_API_CONTRACT.md.
 *
 * <p>Authorization (IS-077): read-only — {@link Permission#OBSERVE} (user + admin).
 */
@RestController
@Tag(name = "Project Overview", description = "Read-only aggregated dashboard counts for a project:"
        + " configured vs running sources, reusable-artifact counts, and how many sources need attention"
        + " (unhealthy).")
@RequestMapping("/api/v1/projects")
public class ProjectOverviewController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";

    private final ProjectOverviewService overview;

    public ProjectOverviewController(ProjectOverviewService overview) {
        this.overview = overview;
    }

    @Operation(summary = "List project overviews",
            description = "Returns per-project aggregated counts: configured and running sources,"
                    + " reusable artifacts, and the number of sources needing attention.")
    @GetMapping("/overview")
    @PreAuthorize(OBSERVE)
    public List<ProjectOverviewResponse> overview() {
        return overview.overview().stream().map(ProjectOverviewResponse::from).toList();
    }

    public record ProjectOverviewResponse(
            String projectId,
            String name,
            int configuredSources,
            int runningSources,
            int reusableArtifacts,
            int sourcesNeedingAttention) {

        static ProjectOverviewResponse from(ProjectOverview o) {
            return new ProjectOverviewResponse(
                    o.projectId(), o.name(), o.configuredSources(), o.runningSources(),
                    o.reusableArtifacts(), o.sourcesNeedingAttention());
        }
    }
}
