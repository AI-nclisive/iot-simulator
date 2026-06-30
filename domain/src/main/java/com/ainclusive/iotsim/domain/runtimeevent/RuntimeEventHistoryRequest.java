package com.ainclusive.iotsim.domain.runtimeevent;

import java.time.Instant;
import java.util.Objects;

/**
 * Input to {@link RuntimeEventHistoryService}: a project-scoped runtime-event
 * history query (IS-055). All filters but {@code projectId} are optional and
 * AND-combined; {@code from} is inclusive and {@code to} exclusive. {@code cursor}
 * is the opaque keyset cursor from a previous page; {@code limit} is the requested
 * page size (clamped by the service, {@code null} → default).
 */
public record RuntimeEventHistoryRequest(
        String projectId,
        String dataSourceId,
        String runId,
        String type,
        Instant from,
        Instant to,
        String cursor,
        Integer limit) {

    public RuntimeEventHistoryRequest {
        Objects.requireNonNull(projectId, "projectId");
    }
}
