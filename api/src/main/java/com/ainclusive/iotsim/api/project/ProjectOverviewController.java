package com.ainclusive.iotsim.api.project;

import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.project.ProjectOverview;
import com.ainclusive.iotsim.domain.project.ProjectOverviewService;
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
@RequestMapping("/api/v1/projects")
public class ProjectOverviewController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";

    private final ProjectOverviewService overview;

    public ProjectOverviewController(ProjectOverviewService overview) {
        this.overview = overview;
    }

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
