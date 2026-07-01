package com.ainclusive.iotsim.api.stream;

import com.ainclusive.iotsim.api.clients.ClientConnectionDto;
import com.ainclusive.iotsim.api.security.Permission;
import com.ainclusive.iotsim.domain.clientobservation.ClientObservationService;
import java.time.Instant;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Live client connect/disconnect stream for a data source (SSE, IS-052). On connect
 * the client gets a {@code clients-snapshot} (the clients currently connected) and
 * then {@code CONNECTED}/{@code DISCONNECTED} deltas. {@code Last-Event-ID} is
 * intentionally ignored — the snapshot is the resync. See backend-specs/05_API_CONTRACT.md.
 *
 * <p>Authorization (IS-077): read-only SSE — {@link Permission#OBSERVE} (user + admin).
 */
@RestController
public class ClientStreamController {

    private static final String OBSERVE =
            "@permissionService.hasPermission(authentication,"
            + " T(com.ainclusive.iotsim.api.security.Permission).OBSERVE)";

    private final LiveStreamSubscriptions subscriptions;
    private final ClientObservationService clients;

    public ClientStreamController(LiveStreamSubscriptions subscriptions, ClientObservationService clients) {
        this.subscriptions = subscriptions;
        this.clients = clients;
    }

    @GetMapping(value = "/api/v1/data-sources/{id}/stream/clients",
            produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize(OBSERVE)
    public SseEmitter streamClients(@PathVariable String id,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventId) {
        // The snapshot is computed at registration (inside the stream lock), not here,
        // so a connect/disconnect between snapshot and subscribe can't be lost — there is
        // no Last-Event-ID replay on this stream to recover it.
        return subscriptions.subscribe(StreamKey.clients(id), null, () -> {
            List<ClientConnectionDto> snapshot =
                    clients.current(id).stream().map(ClientConnectionDto::from).toList();
            return List.of(new LiveEvent(LiveEvent.NO_SEQ, "clients-snapshot", snapshot, Instant.now()));
        });
    }
}
