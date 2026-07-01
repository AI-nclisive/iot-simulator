package com.ainclusive.iotsim.api.synthetic;

import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.synthetic.SyntheticLiveRunService;
import com.ainclusive.iotsim.domain.synthetic.SyntheticLiveRunSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Starts a continuous, real-time-paced live synthetic run on a data source (Model B,
 * IS-119): values flow over wall-clock time until stopped via {@code POST /runs/{id}/stop}
 * (optional {@code maxDurationMs} safety cap). The bounded one-shot batch remains the
 * primitive used by scenario steps.
 *
 * <p>Authorization (IS-077): running synthetic data is a runtime-operate action —
 * {@link Permission#REPLAY_START} (user + admin).
 */
@RestController
@Tag(name = "Synthetic Runs", description = "Start a continuous live synthetic (generated) feed on a data source.")
@RequestMapping("/api/v1/projects/{projectId}/data-sources/{dataSourceId}/run-synthetic")
public class SyntheticRunController {

    private static final String REPLAY_START =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).REPLAY_START)";

    private final SyntheticLiveRunService liveRuns;

    public SyntheticRunController(SyntheticLiveRunService liveRuns) {
        this.liveRuns = liveRuns;
    }

    @Operation(summary = "Start a live synthetic run",
            description = "Starts a continuous, real-time-paced synthetic feed on the data source. The run stays"
                    + " RUNNING until stopped via POST /runs/{id}/stop; an optional maxDurationMs caps it."
                    + " Returns immediately with the created run.")
    @PostMapping
    @PreAuthorize(REPLAY_START)
    public SyntheticRunResponse run(
            @PathVariable String projectId, @PathVariable String dataSourceId,
            @RequestBody(required = false) SyntheticRunRequest req) {
        Long maxDurationMs = req != null ? req.maxDurationMs() : null;
        SyntheticLiveRunSummary summary = liveRuns.start(projectId, dataSourceId, maxDurationMs, "MANUAL", "local");
        return new SyntheticRunResponse(summary.dataSourceId(), 0L,
                summary.seed(), summary.runId(), summary.evidenceId(), summary.state());
    }

    /** Optional wall-clock safety cap in milliseconds; null/omitted = unbounded (stop via /runs/{id}/stop). */
    public record SyntheticRunRequest(Long maxDurationMs) {}

    public record SyntheticRunResponse(
            String dataSourceId, long valueCount, long seed, String runId, String evidenceId, String state) {}
}
