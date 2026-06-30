package com.ainclusive.iotsim.persistence.runtimeevent;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Filter + keyset page request for the runtime-event history query (IS-055).
 * {@code projectId} scopes every query; the remaining filters are optional and
 * AND-combined. {@code from} is inclusive, {@code to} is exclusive. Results are
 * newest first ({@code at} desc, {@code id} desc); {@code beforeAt}/{@code beforeId}
 * carry the keyset cursor (the last row of the previous page).
 */
public record RuntimeEventQuery(
        String projectId,
        String dataSourceId,
        String runId,
        String type,
        OffsetDateTime from,
        OffsetDateTime to,
        OffsetDateTime beforeAt,
        Long beforeId,
        int limit) {

    public static final int DEFAULT_LIMIT = 50;

    public RuntimeEventQuery {
        Objects.requireNonNull(projectId, "projectId");
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
    }

    public static Builder forProject(String projectId) {
        return new Builder(projectId);
    }

    /** Mutable builder; every filter is optional, {@code limit} defaults to {@value #DEFAULT_LIMIT}. */
    public static final class Builder {
        private final String projectId;
        private String dataSourceId;
        private String runId;
        private String type;
        private OffsetDateTime from;
        private OffsetDateTime to;
        private OffsetDateTime beforeAt;
        private Long beforeId;
        private int limit = DEFAULT_LIMIT;

        private Builder(String projectId) {
            this.projectId = projectId;
        }

        public Builder dataSourceId(String dataSourceId) {
            this.dataSourceId = dataSourceId;
            return this;
        }

        public Builder runId(String runId) {
            this.runId = runId;
            return this;
        }

        public Builder type(String type) {
            this.type = type;
            return this;
        }

        public Builder from(OffsetDateTime from) {
            this.from = from;
            return this;
        }

        public Builder to(OffsetDateTime to) {
            this.to = to;
            return this;
        }

        /** Keyset cursor: fetch events strictly older than this {@code (at, id)} position. */
        public Builder before(OffsetDateTime at, long id) {
            this.beforeAt = at;
            this.beforeId = id;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public RuntimeEventQuery build() {
            return new RuntimeEventQuery(
                    projectId, dataSourceId, runId, type, from, to, beforeAt, beforeId, limit);
        }
    }
}
