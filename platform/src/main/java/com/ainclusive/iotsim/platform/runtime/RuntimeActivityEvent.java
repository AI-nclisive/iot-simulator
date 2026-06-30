package com.ainclusive.iotsim.platform.runtime;

import java.time.Instant;
import java.util.Objects;

/**
 * A protocol-neutral runtime event observed at a running data source: the source
 * started or stopped serving, or hit a runtime error. This is the supervisor-side
 * projection of the worker's {@code RuntimeEvents} stream (IS-048), kept free of
 * wire/proto types so the domain can consume it without depending on the IPC
 * contract.
 *
 * <p>Append-only — it feeds the runtime-event history (IS-055). {@code type} is a
 * free string (e.g. {@code SOURCE_START}, {@code SOURCE_STOP}, {@code ERROR}) so
 * new event kinds need no model change; {@code detail} is optional human-readable
 * context; {@code at} is when the worker observed the event. {@code origin}
 * attributes health/error events ({@code SIMULATOR} for supervisor-detected,
 * {@code PROTOCOL} for worker-reported); it is {@code null} for non-health events.
 *
 * <p>See backend-specs/02_WORKER_CONTRACT_AND_IPC.md.
 */
public record RuntimeActivityEvent(
        String dataSourceId, String type, Instant at, String detail, HealthOrigin origin) {

    public RuntimeActivityEvent {
        Objects.requireNonNull(dataSourceId, "dataSourceId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(at, "at");
        if (type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
    }

    /**
     * A non-health/error event with no origin attribution (origin {@code null}).
     * Worker lifecycle events (SOURCE_START/STOP) use this form.
     */
    public RuntimeActivityEvent(String dataSourceId, String type, Instant at, String detail) {
        this(dataSourceId, type, at, detail, null);
    }
}
