package com.ainclusive.iotsim.persistence.activityevent;

import java.time.OffsetDateTime;

/**
 * Filter + keyset page request for the activity-event history query (IS-083).
 * All fields are optional and AND-combined. {@code from} is inclusive, {@code to}
 * is exclusive. Results are newest first ({@code at} desc, {@code id} desc);
 * {@code beforeAt}/{@code beforeId} carry the keyset cursor (last row of previous page).
 */
public record ActivityEventQuery(
        String projectId,
        String actor,
        String action,
        String objectType,
        OffsetDateTime from,
        OffsetDateTime to,
        OffsetDateTime beforeAt,
        Long beforeId,
        int limit) {

    public static final int DEFAULT_LIMIT = 50;

    public ActivityEventQuery {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive: " + limit);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Mutable builder; all filters are optional, {@code limit} defaults to {@value #DEFAULT_LIMIT}. */
    public static final class Builder {
        private String projectId;
        private String actor;
        private String action;
        private String objectType;
        private OffsetDateTime from;
        private OffsetDateTime to;
        private OffsetDateTime beforeAt;
        private Long beforeId;
        private int limit = DEFAULT_LIMIT;

        private Builder() {}

        public Builder projectId(String projectId) {
            this.projectId = projectId;
            return this;
        }

        public Builder actor(String actor) {
            this.actor = actor;
            return this;
        }

        public Builder action(String action) {
            this.action = action;
            return this;
        }

        public Builder objectType(String objectType) {
            this.objectType = objectType;
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

        public ActivityEventQuery build() {
            return new ActivityEventQuery(
                    projectId, actor, action, objectType, from, to, beforeAt, beforeId, limit);
        }
    }
}
