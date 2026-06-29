package com.ainclusive.iotsim.api.stream;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live client connect/disconnect stream for a data source (SSE). Connected-client
 * snapshot/history is IS-052. See backend-specs/05_API_CONTRACT.md.
 */
@RestController
public class ClientStreamController {

    private final LiveStreamSubscriptions subscriptions;

    public ClientStreamController(LiveStreamSubscriptions subscriptions) {
        this.subscriptions = subscriptions;
    }

    @GetMapping(value = "/api/v1/data-sources/{id}/stream/clients",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamClients(@PathVariable String id,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        return subscriptions.subscribe(StreamKey.clients(id), lastEventId);
    }
}
