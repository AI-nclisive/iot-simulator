package com.ainclusive.iotsim.persistence.evidence;

import static com.ainclusive.iotsim.persistence.jooq.tables.Evidence.EVIDENCE;

import com.ainclusive.iotsim.persistence.jooq.tables.records.EvidenceRecord;
import com.ainclusive.iotsim.platform.Ids;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.jooq.JSONB;
import org.springframework.stereotype.Repository;

/** jOOQ-backed {@link EvidenceRepository} (backend-specs/04). */
@Repository
public class JooqEvidenceRepository implements EvidenceRepository {

    private final DSLContext dsl;

    public JooqEvidenceRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public EvidenceRow create(String projectId, String runId, String createdBy) {
        InsertSetMoreStep<EvidenceRecord> insert = dsl.insertInto(EVIDENCE)
                .set(EVIDENCE.ID, Ids.newId())
                .set(EVIDENCE.PROJECT_ID, projectId)
                .set(EVIDENCE.RUN_ID, runId);
        // A null creator falls through to the column's DB default ('local').
        if (createdBy != null) {
            insert = insert.set(EVIDENCE.CREATED_BY, createdBy);
        }
        return map(insert.returning().fetchOne());
    }

    @Override
    public Optional<EvidenceRow> findById(String id) {
        return dsl.selectFrom(EVIDENCE).where(EVIDENCE.ID.eq(id)).fetchOptional().map(this::map);
    }

    @Override
    public Optional<EvidenceRow> findByRun(String runId) {
        return dsl.selectFrom(EVIDENCE)
                .where(EVIDENCE.RUN_ID.eq(runId))
                .orderBy(EVIDENCE.CREATED_AT.desc(), EVIDENCE.ID.desc())
                .limit(1)
                .fetchOptional()
                .map(this::map);
    }

    @Override
    public List<EvidenceRow> findByProject(String projectId) {
        return dsl.selectFrom(EVIDENCE)
                .where(EVIDENCE.PROJECT_ID.eq(projectId))
                .orderBy(EVIDENCE.CREATED_AT.desc(), EVIDENCE.ID.desc())
                .fetch()
                .map(this::map);
    }

    @Override
    public EvidenceRow updateManifest(String id, String manifestJson) {
        return map(dsl.update(EVIDENCE)
                .set(EVIDENCE.MANIFEST, json(manifestJson))
                .where(EVIDENCE.ID.eq(id))
                .returning()
                .fetchSingle());
    }

    @Override
    public EvidenceRow updateStatus(String id, String status, String objectRef) {
        return map(dsl.update(EVIDENCE)
                .set(EVIDENCE.STATUS, status)
                .set(EVIDENCE.OBJECT_REF, objectRef)
                .where(EVIDENCE.ID.eq(id))
                .returning()
                .fetchSingle());
    }

    private static JSONB json(String value) {
        return JSONB.valueOf(value != null ? value : "{}");
    }

    private EvidenceRow map(EvidenceRecord r) {
        return new EvidenceRow(
                r.getId(),
                r.getProjectId(),
                r.getRunId(),
                r.getStatus(),
                r.getManifest() == null ? null : r.getManifest().data(),
                r.getObjectRef(),
                r.getCreatedAt(),
                r.getCreatedBy());
    }
}
