package com.ainclusive.iotsim.persistence.recording;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Stores recording metadata (the captured values live in the value timeline). */
public interface RecordingRepository {

    RecordingRow create(String projectId, String dataSourceId, int schemaVersion,
            String origin, String createdBy);

    Optional<RecordingRow> findById(String id);

    List<RecordingRow> findByProject(String projectId);

    /** Cursor-paged list (IS-074). Sort: {@code created_at DESC, id DESC}. */
    List<RecordingRow> findByProjectPaged(String projectId, OffsetDateTime afterAt, String afterId, int limit);

    RecordingRow finalizeStats(String id, OffsetDateTime timeStart, OffsetDateTime timeEnd,
            long valueCount, long sizeBytes);
}
