package com.ainclusive.iotsim.persistence.recording;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Stores recording metadata (the captured values live in the value timeline). */
public interface RecordingRepository {

    /**
     * @param dataSourceId optional (nullable) reference to the source the recording was
     *                     originally captured from (IS-160); no longer a hard FK requirement
     * @param protocol required protocol type (e.g. {@code OPC_UA}, {@code MODBUS_TCP}) the
     *                 recording is scoped to for replay/import compatibility checks (IS-160)
     */
    RecordingRow create(String projectId, String dataSourceId, String protocol, int schemaVersion,
            String origin, String scanType, String name, String createdBy);

    Optional<RecordingRow> findById(String id);

    List<RecordingRow> findByProject(String projectId);

    /** Cursor-paged list (IS-074). Sort: {@code created_at DESC, id DESC}. */
    List<RecordingRow> findByProjectPaged(String projectId, OffsetDateTime afterAt, String afterId, int limit);

    RecordingRow finalizeStats(String id, OffsetDateTime timeStart, OffsetDateTime timeEnd,
            long valueCount, long sizeBytes);

    /** Deletes the recording row. Returns {@code false} if it did not exist (IS-092). */
    boolean deleteById(String id);

    /** Recording count for a project without fetching the rows (IS-092: retention dashboard count). */
    long countByProject(String projectId);
}
