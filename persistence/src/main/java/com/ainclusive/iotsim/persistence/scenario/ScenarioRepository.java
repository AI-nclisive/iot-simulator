package com.ainclusive.iotsim.persistence.scenario;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/** Stores scenarios and their ordered steps (backend-specs/03, 04). */
public interface ScenarioRepository {
    ScenarioRow create(String projectId, String name, String deterministicSettings,
            List<ScenarioStepInput> steps, String createdBy);

    Optional<ScenarioRow> findById(String id);

    List<ScenarioRow> findByProject(String projectId);

    /** Cursor-paged list (IS-074). Sort: {@code created_at DESC, id DESC}. */
    List<ScenarioRow> findByProjectPaged(String projectId, OffsetDateTime afterAt, String afterId, int limit);

    /** Null name/deterministicSettings/steps leave that field unchanged; non-null steps replace the list. */
    Optional<ScenarioRow> update(String id, String name, String deterministicSettings,
            List<ScenarioStepInput> steps, long expectedVersion);

    boolean deleteById(String id);
}
