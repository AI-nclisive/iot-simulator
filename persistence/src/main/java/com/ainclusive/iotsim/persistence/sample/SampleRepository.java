package com.ainclusive.iotsim.persistence.sample;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Stores named sample subsets/snapshots. */
public interface SampleRepository {
    SampleRow create(String projectId, String derivedFromRecordingId, String name,
            String selection, String tags, String createdBy);
    Optional<SampleRow> findById(String id);
    List<SampleRow> findByProject(String projectId);
    /** Cursor-paged list (IS-074). Sort: {@code created_at DESC, id DESC}. */
    List<SampleRow> findByProjectPaged(String projectId, OffsetDateTime afterAt, String afterId, int limit);
    boolean deleteById(String id);
}
