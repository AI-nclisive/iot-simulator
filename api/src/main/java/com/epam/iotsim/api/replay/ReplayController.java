package com.epam.iotsim.api.replay;

import com.epam.iotsim.domain.replay.ReplayService;
import com.epam.iotsim.domain.replay.ReplaySummary;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Replays a recording through a data-source (backend-specs/05_API_CONTRACT.md). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/data-sources/{dataSourceId}/replay")
public class ReplayController {

    private final ReplayService replays;

    public ReplayController(ReplayService replays) {
        this.replays = replays;
    }

    @PostMapping
    public ReplayResponse replay(
            @PathVariable String projectId, @PathVariable String dataSourceId,
            @RequestBody ReplayRequest req) {
        if (req == null || req.recordingId() == null || req.recordingId().isBlank()) {
            throw new IllegalArgumentException("recordingId is required");
        }
        ReplaySummary summary = replays.replay(projectId, dataSourceId, req.recordingId());
        return new ReplayResponse(summary.recordingId(), summary.dataSourceId(), summary.valueCount());
    }

    public record ReplayRequest(String recordingId) {}

    public record ReplayResponse(String recordingId, String dataSourceId, long valueCount) {}
}
