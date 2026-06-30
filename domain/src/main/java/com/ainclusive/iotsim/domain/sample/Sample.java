package com.ainclusive.iotsim.domain.sample;

import java.time.Instant;
import java.util.List;

/** Named reusable subset/snapshot derived from a recording (backend-specs/03). */
public record Sample(
        String id,
        String projectId,
        String derivedFromRecordingId,
        String name,
        String selection,
        List<String> tags,
        Instant createdAt,
        String createdBy,
        long version) {}
