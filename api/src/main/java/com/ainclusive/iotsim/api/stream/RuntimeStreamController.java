package com.ainclusive.iotsim.api.stream;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live runtime-context stream for a project (SSE). Carries runtime activity events
 * plus {@code heartbeat}/{@code resync}; richer aggregation (active runs, health)
 * lands in IS-051/IS-053/IS-054. See backend-specs/05_API_CONTRACT.md.
 */
@RestController
public class RuntimeStreamController {

    private final LiveStreamSubscriptions subscriptions;

    public RuntimeStreamController(LiveStreamSubscriptions subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping(value = "/api/v1/projects/{projectId}/stream/runtime",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamRuntime(@PathVariable String projectId,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return subscriptions.subscribe(StreamKey.runtime(projectId), lastEventId);
    }
}
