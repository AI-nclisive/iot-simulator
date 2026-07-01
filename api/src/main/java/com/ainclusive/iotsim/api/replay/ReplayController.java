package com.ainclusive.iotsim.api.replay;

import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.replay.ReplayService;
import com.ainclusive.iotsim.domain.replay.ReplaySummary;
import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Replays a recording through a data-source (backend-specs/05_API_CONTRACT.md).
 *
 * <p>Authorization (IS-077): replay is a runtime-operate action —
 * {@link Permission#REPLAY_START} (user + admin).
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/data-sources/{dataSourceId}/replay")
public class ReplayController {

    private static final String REPLAY_START =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).REPLAY_START)";

    private final ReplayService replays;

    public ReplayController(ReplayService replays) {
        this.replays = replays;
    }

    @PostMapping
    @PreAuthorize(REPLAY_START)
    public ReplayResponse replay(
            @PathVariable String projectId, @PathVariable String dataSourceId,
            @RequestBody ReplayRequest req) {
        if (req == null || req.recordingId() == null || req.recordingId().isBlank()) {
            throw new IllegalArgumentException("recordingId is required");
        }
        if ((req.seed() == null) != (req.startTime() == null)) {
            throw new IllegalArgumentException("seed and startTime must both be provided or both omitted");
        }
        DeterministicSettings settings = req.seed() != null
                ? new DeterministicSettings(req.seed(), req.startTime())
                : null;
        ReplaySummary summary = replays.replay(projectId, dataSourceId, req.recordingId(),
                settings, Boolean.TRUE.equals(req.compatibilityAck()));
        return new ReplayResponse(summary.recordingId(), summary.dataSourceId(),
                summary.valueCount(), summary.runId(), summary.evidenceId(),
                summary.deterministicSettings().seed(),
                summary.deterministicSettings().startTime());
    }

    /**
     * @param recordingId     the recording to replay (required)
     * @param seed            deterministic seed; omit to let the server generate one
     * @param startTime       logical clock start-time; omit to use current wall time
     * @param compatibilityAck {@code true} to proceed even when the recording's schema version
     *                        differs from the data source's current schema version
     */
    public record ReplayRequest(
            String recordingId,
            Long seed,
            Instant startTime,
            Boolean compatibilityAck) {}

    public record ReplayResponse(
            String recordingId,
            String dataSourceId,
            long valueCount,
            String runId,
            String evidenceId,
            long seed,
            Instant startTime) {}
}
