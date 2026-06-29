package com.ainclusive.iotsim.domain.clientobservation;

import java.time.Instant;

/**
 * Domain projection of one observed client connection at a data source (IS-052):
 * which client connected, when, and whether it is still connected. {@code
 * disconnectedAt} is {@code null} while {@code connected} is {@code true}. Kept free
 * of persistence types so the API layer can consume it directly.
 */
public record ClientConnectionView(
        String clientId, Instant connectedAt, Instant disconnectedAt, boolean connected) {
}
