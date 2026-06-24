package com.ainclusive.iotsim.persistence.recording;

import static com.ainclusive.iotsim.persistence.jooq.tables.Recordings.RECORDINGS;

import com.ainclusive.iotsim.persistence.jooq.tables.records.RecordingsRecord;
import com.ainclusive.iotsim.platform.Ids;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@Repository
public class JooqRecordingRepository implements RecordingRepository {

    private final DSLContext dsl;

    public JooqRecordingRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public RecordingRow create(String projectId, String dataSourceId, int schemaVersion,
            String origin, String createdBy) {
        RecordingsRecord record = dsl.insertInto(RECORDINGS)
                .set(RECORDINGS.ID, Ids.newId())
                .set(RECORDINGS.PROJECT_ID, projectId)
                .set(RECORDINGS.DATA_SOURCE_ID, dataSourceId)
                .set(RECORDINGS.SCHEMA_VERSION, schemaVersion)
                .set(RECORDINGS.ORIGIN, origin)
                .set(RECORDINGS.CREATED_BY, createdBy)
                .returning()
                .fetchOne();
        return map(record);
    }

    @Override
    public Optional<RecordingRow> findById(String id) {
        return dsl.selectFrom(RECORDINGS).where(RECORDINGS.ID.eq(id)).fetchOptional().map(this::map);
    }

    @Override
    public List<RecordingRow> findByProject(String projectId) {
        return dsl.selectFrom(RECORDINGS)
                .where(RECORDINGS.PROJECT_ID.eq(projectId))
                .orderBy(RECORDINGS.CREATED_AT.desc())
                .fetch()
                .map(this::map);
    }

    @Override
    public RecordingRow finalizeStats(String id, OffsetDateTime timeStart, OffsetDateTime timeEnd,
            long valueCount, long sizeBytes) {
        RecordingsRecord record = dsl.update(RECORDINGS)
                .set(RECORDINGS.TIME_START, timeStart)
                .set(RECORDINGS.TIME_END, timeEnd)
                .set(RECORDINGS.VALUE_COUNT, valueCount)
                .set(RECORDINGS.SIZE_BYTES, sizeBytes)
                .set(RECORDINGS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .set(RECORDINGS.VERSION, RECORDINGS.VERSION.plus(1))
                .where(RECORDINGS.ID.eq(id))
                .returning()
                .fetchOne();
        return map(record);
    }

    private RecordingRow map(RecordingsRecord r) {
        return new RecordingRow(
                r.getId(),
                r.getProjectId(),
                r.getDataSourceId(),
                r.getSchemaVersion(),
                r.getOrigin(),
                r.getTimeStart(),
                r.getTimeEnd(),
                r.getValueCount(),
                r.getSizeBytes(),
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getCreatedBy(),
                r.getVersion());
    }
}
