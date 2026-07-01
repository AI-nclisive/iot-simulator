package com.ainclusive.iotsim.api.synthetic;

import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.synthetic.SyntheticRunService;
import com.ainclusive.iotsim.domain.synthetic.SyntheticRunSummary;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Runs a synthetic source's generated series as a bounded batch (Model A; IS-065).
 * The generated twin of the replay endpoint.
 *
 * <p>Authorization (IS-077): running synthetic data is a runtime-operate action —
 * {@link Permission#REPLAY_START} (user + admin).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/data-sources/{dataSourceId}/run-synthetic")
public class SyntheticRunController {

    private static final String REPLAY_START =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).REPLAY_START)";

    private final SyntheticRunService syntheticRuns;

    public SyntheticRunController(SyntheticRunService syntheticRuns) {
        this.syntheticRuns = syntheticRuns;
    }

    @PostMapping
    @PreAuthorize(REPLAY_START)
    public SyntheticRunResponse run(
            @PathVariable String projectId, @PathVariable String dataSourceId,
            @RequestBody SyntheticRunRequest req) {
        SyntheticRunSummary summary = syntheticRuns.run(projectId, dataSourceId, req.durationMs());
        return new SyntheticRunResponse(summary.dataSourceId(), summary.valueCount(),
                summary.seed(), summary.runId(), summary.evidenceId());
    }

    public record SyntheticRunRequest(long durationMs) {}

    public record SyntheticRunResponse(
            String dataSourceId, long valueCount, long seed, String runId, String evidenceId) {}
}
