package com.ainclusive.iotsim.persistence.sample;

import static com.ainclusive.iotsim.persistence.jooq.tables.Samples.SAMPLES;

import com.ainclusive.iotsim.persistence.jooq.tables.records.SamplesRecord;
import com.ainclusive.iotsim.platform.Ids;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

@Repository
public class JooqSampleRepository implements SampleRepository {

    private final DSLContext dsl;

    public JooqSampleRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public SampleRow create(String projectId, String derivedFromRecordingId, String name,
            String selection, String tags, String createdBy) {
        SamplesRecord record = dsl.insertInto(SAMPLES)
                .set(SAMPLES.ID, Ids.newId())
                .set(SAMPLES.PROJECT_ID, projectId)
                .set(SAMPLES.DERIVED_FROM_RECORDING_ID, derivedFromRecordingId)
                .set(SAMPLES.NAME, name)
                .set(SAMPLES.SELECTION, json(selection, "{}"))
                .set(SAMPLES.TAGS, json(tags, "[]"))
                .set(SAMPLES.CREATED_BY, createdBy)
                .returning()
                .fetchOne();
        return map(record);
    }

    @Override
    public Optional<SampleRow> findById(String id) {
        return dsl.selectFrom(SAMPLES).where(SAMPLES.ID.eq(id)).fetchOptional().map(this::map);
    }

    @Override
    public List<SampleRow> findByProject(String projectId) {
        return dsl.selectFrom(SAMPLES)
                .where(SAMPLES.PROJECT_ID.eq(projectId))
                .orderBy(SAMPLES.CREATED_AT.desc())
                .fetch()
                .map(this::map);
    }

    @Override
    public boolean deleteById(String id) {
        return dsl.deleteFrom(SAMPLES).where(SAMPLES.ID.eq(id)).execute() > 0;
    }

    private SampleRow map(SamplesRecord r) {
        return new SampleRow(
                r.getId(),
                r.getProjectId(),
                r.getDerivedFromRecordingId(),
                r.getName(),
                r.getSelection() != null ? r.getSelection().data() : null,
                r.getTags() != null ? r.getTags().data() : null,
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getCreatedBy(),
                r.getVersion());
    }

    private static JSONB json(String value, String fallback) {
        return JSONB.valueOf(value != null ? value : fallback);
    }
}
