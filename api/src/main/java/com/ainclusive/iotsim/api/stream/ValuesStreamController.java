package com.ainclusive.iotsim.api.stream;

import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live (conflated) values for a data source's Values tab (SSE, IS-051). On connect the
 * client gets a {@code values-snapshot} (current latest-per-node) and then {@code values}
 * deltas. {@code Last-Event-ID} is intentionally ignored — the snapshot is the resync.
 * See backend-specs/05_API_CONTRACT.md.
 */
@RestController
public class ValuesStreamController {

    private final LiveStreamSubscriptions subscriptions;
    private final LiveValueStore store;

    public ValuesStreamController(LiveStreamSubscriptions subscriptions, LiveValuesHub valuesHub) {
        this(subscriptions, valuesHub.store());
    }

    ValuesStreamController(LiveStreamSubscriptions subscriptions, LiveValueStore store) {
        this.subscriptions = subscriptions;
        this.store = store;
    }

    @GetMapping(value = "/api/v1/data-sources/{id}/stream/values",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamValues(@PathVariable String id,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        List<StreamValue> snapshot = store.snapshot(id).stream().map(StreamValue::from).toList();
        LiveEvent snapshotEvent =
                new LiveEvent(LiveEvent.NO_SEQ, "values-snapshot", snapshot, Instant.now());
        return subscriptions.subscribe(StreamKey.values(id), null, List.of(snapshotEvent));
    }
}
