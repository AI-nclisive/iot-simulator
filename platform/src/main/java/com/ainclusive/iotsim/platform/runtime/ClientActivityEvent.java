package com.ainclusive.iotsim.platform.runtime;

import java.time.Instant;
import java.util.Objects;

/**
 * A protocol-neutral client-activity event observed at a running data-source's
 * protocol endpoint: a protocol client connected, disconnected, or changed its
 * subscriptions. This is the supervisor-side projection of the worker's
 * {@code ClientEvents} stream (IS-047), kept free of wire/proto types so the
 * domain can consume it without depending on the IPC contract.
 *
 * <p>Point-in-time and append-only — it feeds connected-client observation
 * (IS-052) and live event surfaces (IS-046). {@code clientId} is the protocol's
 * own identifier for the connecting client (may be empty if the protocol does not
 * supply one); {@code at} is when the worker observed the event.
 *
 * <p>See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
public record ClientActivityEvent(String dataSourceId, Kind kind, String clientId, Instant at) {

    /** Mirrors the wire {@code ClientEvent.Kind}, minus the unspecified sentinel. */
    public enum Kind {
        CONNECTED,
        DISCONNECTED,
        SUBSCRIPTION
    }

    public ClientActivityEvent {
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(at, "at");
        clientId = clientId == null ? "" : clientId;
    }
}
