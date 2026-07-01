package com.ainclusive.iotsim.persistence.run;

import static com.ainclusive.iotsim.persistence.jooq.tables.RunSources.RUN_SOURCES;
import static com.ainclusive.iotsim.persistence.jooq.tables.Runs.RUNS;

import com.ainclusive.iotsim.persistence.jooq.tables.records.RunsRecord;
import com.ainclusive.iotsim.platform.Ids;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.InsertSetMoreStep;
import org.jooq.Result;
import org.springframework.stereotype.Repository;

/** jOOQ-backed {@link RunRepository} (backend-specs/04). */
@Repository
public class JooqRunRepository implements RunRepository {

    private final DSLContext dsl;

    public JooqRunRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public RunRow create(String projectId, String kind, String trigger, String initiator,
            List<String> sourceIds, String scenarioId, String parentRunId) {
        String id = Ids.newId();
        // Dedup while preserving order: a source named twice is one participation,
        // not a run_sources primary-key violation that would roll back the whole run.
        List<String> sources = sourceIds == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(sourceIds));
        // Run row and its run_sources land atomically so a run is never observed
        // without its participating sources.
        dsl.transaction(cfg -> {
            DSLContext tx = cfg.dsl();
            InsertSetMoreStep<RunsRecord> insert = tx.insertInto(RUNS)
                    .set(RUNS.ID, id)
                    .set(RUNS.PROJECT_ID, projectId)
                    .set(RUNS.KIND, kind)
                    .set(RUNS.SCENARIO_ID, scenarioId)
                    .set(RUNS.PARENT_RUN_ID, parentRunId);
            // Null trigger/initiator fall through to the column defaults.
            if (trigger != null) {
                insert = insert.set(RUNS.TRIGGER, trigger);
            }
            if (initiator != null) {
                insert = insert.set(RUNS.INITIATOR, initiator);
            }
            insert.execute();
            for (String sourceId : sources) {
                tx.insertInto(RUN_SOURCES)
                        .set(RUN_SOURCES.RUN_ID, id)
                        .set(RUN_SOURCES.DATA_SOURCE_ID, sourceId)
                        .execute();
            }
        });
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<RunRow> findById(String id) {
        return dsl.selectFrom(RUNS).where(RUNS.ID.eq(id)).fetchOptional().map(this::map);
    }

    @Override
    public List<RunRow> findByProject(String projectId) {
        Result<RunsRecord> records = dsl.selectFrom(RUNS)
                .where(RUNS.PROJECT_ID.eq(projectId))
                .orderBy(RUNS.CREATED_AT.desc(), RUNS.ID.desc())
                .fetch();
        if (records.isEmpty()) {
            return List.of();
        }
        // One grouped query for all run_sources, not a per-row sub-select (no N+1).
        Map<String, List<String>> sourcesByRun = dsl.select(
                        RUN_SOURCES.RUN_ID, RUN_SOURCES.DATA_SOURCE_ID)
                .from(RUN_SOURCES)
                .where(RUN_SOURCES.RUN_ID.in(records.map(RunsRecord::getId)))
                .orderBy(RUN_SOURCES.DATA_SOURCE_ID.asc())
                .fetchGroups(RUN_SOURCES.RUN_ID, RUN_SOURCES.DATA_SOURCE_ID);
        return records.map(r -> map(r, sourcesByRun.getOrDefault(r.getId(), List.of())));
    }

    @Override
    public List<RunRow> findActiveByProject(String projectId) {
        Result<RunsRecord> records = dsl.selectFrom(RUNS)
                .where(RUNS.PROJECT_ID.eq(projectId))
                .and(RUNS.STATE.in("RUNNING", "QUEUED"))
                .orderBy(RUNS.CREATED_AT.desc(), RUNS.ID.desc())
                .fetch();
        if (records.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> sourcesByRun = dsl.select(
                        RUN_SOURCES.RUN_ID, RUN_SOURCES.DATA_SOURCE_ID)
                .from(RUN_SOURCES)
                .where(RUN_SOURCES.RUN_ID.in(records.map(RunsRecord::getId)))
                .orderBy(RUN_SOURCES.DATA_SOURCE_ID.asc())
                .fetchGroups(RUN_SOURCES.RUN_ID, RUN_SOURCES.DATA_SOURCE_ID);
        return records.map(r -> map(r, sourcesByRun.getOrDefault(r.getId(), List.of())));
    }

    @Override
    public List<RunRow> findByProjectPaged(
            String projectId, OffsetDateTime afterAt, String afterId, int limit) {
        var q = dsl.selectFrom(RUNS).where(RUNS.PROJECT_ID.eq(projectId));
        if (afterAt != null) {
            q = q.and(RUNS.CREATED_AT.lt(afterAt)
                    .or(RUNS.CREATED_AT.eq(afterAt).and(RUNS.ID.lt(afterId))));
        }
        Result<RunsRecord> records = q.orderBy(RUNS.CREATED_AT.desc(), RUNS.ID.desc())
                .limit(limit)
                .fetch();
        if (records.isEmpty()) {
            return List.of();
        }
        Map<String, List<String>> sourcesByRun = dsl.select(
                        RUN_SOURCES.RUN_ID, RUN_SOURCES.DATA_SOURCE_ID)
                .from(RUN_SOURCES)
                .where(RUN_SOURCES.RUN_ID.in(records.map(RunsRecord::getId)))
                .orderBy(RUN_SOURCES.DATA_SOURCE_ID.asc())
                .fetchGroups(RUN_SOURCES.RUN_ID, RUN_SOURCES.DATA_SOURCE_ID);
        return records.map(r -> map(r, sourcesByRun.getOrDefault(r.getId(), List.of())));
    }

    @Override
    public RunRow start(String id, OffsetDateTime startedAt) {
        return map(dsl.update(RUNS)
                .set(RUNS.STATE, "RUNNING")
                .set(RUNS.STARTED_AT, startedAt)
                .where(RUNS.ID.eq(id))
                .returning()
                .fetchSingle());
    }

    @Override
    public RunRow end(String id, String terminalState, OffsetDateTime endedAt) {
        return map(dsl.update(RUNS)
                .set(RUNS.STATE, terminalState)
                .set(RUNS.ENDED_AT, endedAt)
                .where(RUNS.ID.eq(id))
                .returning()
                .fetchSingle());
    }

    @Override
    public RunRow linkEvidence(String runId, String evidenceId) {
        return map(dsl.update(RUNS)
                .set(RUNS.EVIDENCE_ID, evidenceId)
                .where(RUNS.ID.eq(runId))
                .returning()
                .fetchSingle());
    }

    private List<String> sourceIds(String runId) {
        return dsl.select(RUN_SOURCES.DATA_SOURCE_ID)
                .from(RUN_SOURCES)
                .where(RUN_SOURCES.RUN_ID.eq(runId))
                .orderBy(RUN_SOURCES.DATA_SOURCE_ID.asc())
                .fetch(RUN_SOURCES.DATA_SOURCE_ID);
    }

    private RunRow map(RunsRecord r) {
        return map(r, sourceIds(r.getId()));
    }

    private RunRow map(RunsRecord r, List<String> sources) {
        return new RunRow(
                r.getId(),
                r.getProjectId(),
                r.getKind(),
                r.getTrigger(),
                r.getInitiator(),
                r.getState(),
                r.getScenarioId(),
                r.getEvidenceId(),
                r.getStartedAt(),
                r.getEndedAt(),
                r.getCreatedAt(),
                sources,
                r.getParentRunId());
    }
}
