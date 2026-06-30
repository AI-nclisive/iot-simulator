package com.ainclusive.iotsim.persistence.scenario;

import static com.ainclusive.iotsim.persistence.jooq.tables.ScenarioSteps.SCENARIO_STEPS;
import static com.ainclusive.iotsim.persistence.jooq.tables.Scenarios.SCENARIOS;

import com.ainclusive.iotsim.persistence.jooq.tables.records.ScenarioStepsRecord;
import com.ainclusive.iotsim.persistence.jooq.tables.records.ScenariosRecord;
import com.ainclusive.iotsim.platform.Ids;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.JSONB;
import org.jooq.Result;
import org.jooq.UpdateSetMoreStep;
import org.springframework.stereotype.Repository;

/** jOOQ-backed {@link ScenarioRepository} (backend-specs/04). */
@Repository
public class JooqScenarioRepository implements ScenarioRepository {

    private final DSLContext dsl;

    public JooqScenarioRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public ScenarioRow create(String projectId, String name, String deterministicSettings,
            List<ScenarioStepInput> steps, String createdBy) {
        String id = Ids.newId();
        List<ScenarioStepInput> stepList = steps != null ? steps : List.of();
        // Scenario row + its ordered steps land atomically.
        dsl.transaction(cfg -> {
            DSLContext tx = cfg.dsl();
            tx.insertInto(SCENARIOS)
                    .set(SCENARIOS.ID, id)
                    .set(SCENARIOS.PROJECT_ID, projectId)
                    .set(SCENARIOS.NAME, name)
                    .set(SCENARIOS.DETERMINISTIC_SETTINGS, json(deterministicSettings))
                    .set(SCENARIOS.CREATED_BY, createdBy)
                    .execute();
            insertSteps(tx, id, stepList);
        });
        return findById(id).orElseThrow();
    }

    @Override
    public Optional<ScenarioRow> findById(String id) {
        ScenariosRecord rec = dsl.selectFrom(SCENARIOS).where(SCENARIOS.ID.eq(id)).fetchOne();
        if (rec == null) {
            return Optional.empty();
        }
        return Optional.of(map(rec, fetchSteps(dsl, id)));
    }

    @Override
    public List<ScenarioRow> findByProject(String projectId) {
        return fetchScenarios(dsl.selectFrom(SCENARIOS)
                .where(SCENARIOS.PROJECT_ID.eq(projectId))
                .orderBy(SCENARIOS.CREATED_AT.desc(), SCENARIOS.ID.desc())
                .fetch());
    }

    @Override
    public List<ScenarioRow> findByProjectPaged(String projectId,
            OffsetDateTime afterAt, String afterId, int limit) {
        var q = dsl.selectFrom(SCENARIOS).where(SCENARIOS.PROJECT_ID.eq(projectId));
        if (afterAt != null) {
            q = q.and(SCENARIOS.CREATED_AT.lt(afterAt)
                    .or(SCENARIOS.CREATED_AT.eq(afterAt).and(SCENARIOS.ID.lt(afterId))));
        }
        return fetchScenarios(q.orderBy(SCENARIOS.CREATED_AT.desc(), SCENARIOS.ID.desc())
                .limit(limit)
                .fetch());
    }

    @Override
    public Optional<ScenarioRow> update(String id, String name, String deterministicSettings,
            List<ScenarioStepInput> steps, long expectedVersion) {
        return dsl.transactionResult(cfg -> {
            DSLContext tx = cfg.dsl();
            UpdateSetMoreStep<ScenariosRecord> upd = tx.update(SCENARIOS)
                    .set(SCENARIOS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                    .set(SCENARIOS.VERSION, SCENARIOS.VERSION.plus(1));
            if (name != null) {
                upd = upd.set(SCENARIOS.NAME, name);
            }
            if (deterministicSettings != null) {
                upd = upd.set(SCENARIOS.DETERMINISTIC_SETTINGS, json(deterministicSettings));
            }
            ScenariosRecord rec = upd
                    .where(SCENARIOS.ID.eq(id).and(SCENARIOS.VERSION.eq(expectedVersion)))
                    .returning()
                    .fetchOne();
            if (rec == null) {
                return Optional.<ScenarioRow>empty();
            }
            if (steps != null) {
                tx.deleteFrom(SCENARIO_STEPS).where(SCENARIO_STEPS.SCENARIO_ID.eq(id)).execute();
                insertSteps(tx, id, steps);
            }
            return Optional.of(map(rec, fetchSteps(tx, id)));
        });
    }

    @Override
    public boolean deleteById(String id) {
        // scenario_steps has ON DELETE CASCADE (V2), so steps go with the scenario.
        return dsl.deleteFrom(SCENARIOS).where(SCENARIOS.ID.eq(id)).execute() > 0;
    }

    private static void insertSteps(DSLContext tx, String scenarioId, List<ScenarioStepInput> steps) {
        for (int i = 0; i < steps.size(); i++) {
            ScenarioStepInput s = steps.get(i);
            tx.insertInto(SCENARIO_STEPS)
                    .set(SCENARIO_STEPS.SCENARIO_ID, scenarioId)
                    .set(SCENARIO_STEPS.ORDINAL, i)
                    .set(SCENARIO_STEPS.TYPE, s.type())
                    .set(SCENARIO_STEPS.TARGET_SOURCE_ID, s.targetSourceId())
                    .set(SCENARIO_STEPS.PARAMS, json(s.params()))
                    .execute();
        }
    }

    private static List<ScenarioStepRow> fetchSteps(DSLContext ctx, String scenarioId) {
        return ctx.selectFrom(SCENARIO_STEPS)
                .where(SCENARIO_STEPS.SCENARIO_ID.eq(scenarioId))
                .orderBy(SCENARIO_STEPS.ORDINAL.asc())
                .fetch()
                .map(JooqScenarioRepository::mapStep);
    }

    private List<ScenarioRow> fetchScenarios(Result<ScenariosRecord> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        // One grouped step query for all scenarios on the page (no N+1).
        Map<String, List<ScenarioStepRow>> stepsByScenario = dsl.selectFrom(SCENARIO_STEPS)
                .where(SCENARIO_STEPS.SCENARIO_ID.in(records.map(ScenariosRecord::getId)))
                .orderBy(SCENARIO_STEPS.SCENARIO_ID.asc(), SCENARIO_STEPS.ORDINAL.asc())
                .fetch()
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        ScenarioStepsRecord::getScenarioId,
                        java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.mapping(JooqScenarioRepository::mapStep,
                                java.util.stream.Collectors.toList())));
        return records.map(r -> map(r, stepsByScenario.getOrDefault(r.getId(), List.of())));
    }

    private static ScenarioStepRow mapStep(ScenarioStepsRecord r) {
        return new ScenarioStepRow(
                r.getOrdinal(),
                r.getType(),
                r.getTargetSourceId(),
                r.getParams() != null ? r.getParams().data() : null);
    }

    private static ScenarioRow map(ScenariosRecord r, List<ScenarioStepRow> steps) {
        return new ScenarioRow(
                r.getId(),
                r.getProjectId(),
                r.getName(),
                r.getStatus(),
                r.getDeterministicSettings() != null ? r.getDeterministicSettings().data() : null,
                steps,
                r.getCreatedAt(),
                r.getUpdatedAt(),
                r.getCreatedBy(),
                r.getVersion());
    }

    private static JSONB json(String value) {
        return JSONB.valueOf(value != null ? value : "{}");
    }
}
