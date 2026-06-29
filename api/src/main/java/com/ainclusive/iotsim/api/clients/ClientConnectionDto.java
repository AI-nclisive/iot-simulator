package com.ainclusive.iotsim.api.clients;

import com.ainclusive.iotsim.domain.clientobservation.ClientConnectionView;
import java.time.Instant;

/**
 * API projection of one observed client connection (IS-052). {@code disconnectedAt}
 * is {@code null} while {@code connected} is {@code true}. Used both by the REST
 * {@code GET .../clients} response and the SSE {@code clients-snapshot} payload.
 */
public record ClientConnectionDto(
        String clientId, Instant connectedAt, Instant disconnectedAt, boolean connected) {

    public static ClientConnectionDto from(ClientConnectionView view) {
        return new ClientConnectionDto(
                view.clientId(), view.connectedAt(), view.disconnectedAt(), view.connected());
    }
}
