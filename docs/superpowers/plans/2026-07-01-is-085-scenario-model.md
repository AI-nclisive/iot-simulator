# IS-085 Scenario model + steps — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver the scenario model + ordered steps + CRUD (create/list/get/update/delete/duplicate) so a scenario can be authored and persisted — the prerequisite for IS-086 (validation + run execution).

**Architecture:** Mirror the existing pure-CRUD artifact `sample` across three layers — `persistence/scenario` (jOOQ repo over the existing `scenarios` + `scenario_steps` tables), `domain/scenario` (records + `ScenarioService` with model-level validation), `api/scenario` (`ScenarioController` + DTOs). No new Flyway migration; jOOQ classes for both tables are already generated.

**Tech Stack:** Java 21, Spring Boot, jOOQ, Jackson 3 (`tools.jackson.*`), JUnit 5 + AssertJ, Testcontainers (Postgres 17), Flyway.

## Global Constraints

- Jackson 3: import `tools.jackson.databind.ObjectMapper`; `readTree`/`writeValueAsString` throw **unchecked** `JacksonException` — never `com.fasterxml.jackson.*`.
- jOOQ repos are Spring `@Repository`; services are `@Service`; constructor injection only (single ctor — no `@Autowired` needed).
- `scenarios.status` is always `DRAFT` in IS-085 (READY/INVALID belong to IS-086).
- `deterministic_settings` and step `params` are **opaque JSON object strings** (passthrough), default `"{}"`.
- Step `type` ∈ {`START`,`STOP`,`REPLAY`,`SYNTHETIC`,`FAULT`,`WAIT`,`MARKER`}.
- Cursor pagination matches IS-074: `created_at DESC, id DESC`, via `PageCursor`/`Page`.
- Optimistic concurrency on update: `ETag:"<version>"` + `If-Match` (mirror `ProjectController`).
- Out of scope: `/validate`, `/run`, READY/INVALID transitions, cross-entity reference checks (targetSourceId existence, recording/fault refs), audit events, UI.
- After all tasks: `./gradlew build` green. The `TASKS.md` checkbox flip + board move happen later via `/open-pr`, not in this plan.

---

## File Structure

```
persistence/src/main/java/com/ainclusive/iotsim/persistence/scenario/
  ScenarioStepInput.java   write-side step (type, targetSourceId, params); ordinal assigned by position
  ScenarioStepRow.java     read-side step projection (ordinal, type, targetSourceId, params)
  ScenarioRow.java         scenario columns + List<ScenarioStepRow> steps
  ScenarioRepository.java  interface
  JooqScenarioRepository.java  impl (one transaction per write)
persistence/src/test/java/com/ainclusive/iotsim/persistence/scenario/
  ScenarioRepositoryIT.java

domain/src/main/java/com/ainclusive/iotsim/domain/scenario/
  ScenarioStep.java        domain step (ordinal, type, targetSourceId, params)
  Scenario.java            domain aggregate (incl. List<ScenarioStep> steps)
  ScenarioService.java     CRUD + duplicate + model-level validation
domain/src/test/java/com/ainclusive/iotsim/domain/scenario/
  ScenarioServiceTest.java

api/src/main/java/com/ainclusive/iotsim/api/scenario/
  ScenarioController.java  + CreateScenarioRequest / UpdateScenarioRequest / StepDto /
                             ScenarioResponse / StepResponse (nested records)
```

---

## Task 1: Persistence layer (`scenario` repository)

**Files:**
- Create: `persistence/src/main/java/com/ainclusive/iotsim/persistence/scenario/ScenarioStepInput.java`
- Create: `persistence/src/main/java/com/ainclusive/iotsim/persistence/scenario/ScenarioStepRow.java`
- Create: `persistence/src/main/java/com/ainclusive/iotsim/persistence/scenario/ScenarioRow.java`
- Create: `persistence/src/main/java/com/ainclusive/iotsim/persistence/scenario/ScenarioRepository.java`
- Create: `persistence/src/main/java/com/ainclusive/iotsim/persistence/scenario/JooqScenarioRepository.java`
- Test: `persistence/src/test/java/com/ainclusive/iotsim/persistence/scenario/ScenarioRepositoryIT.java`

**Interfaces:**
- Consumes: generated jOOQ `Scenarios.SCENARIOS`, `ScenarioSteps.SCENARIO_STEPS`, records `ScenariosRecord`/`ScenarioStepsRecord`; `com.ainclusive.iotsim.platform.Ids.newId()`; `org.jooq.JSONB`.
- Produces:
  - `record ScenarioStepInput(String type, String targetSourceId, String params)`
  - `record ScenarioStepRow(int ordinal, String type, String targetSourceId, String params)`
  - `record ScenarioRow(String id, String projectId, String name, String status, String deterministicSettings, java.util.List<ScenarioStepRow> steps, java.time.OffsetDateTime createdAt, java.time.OffsetDateTime updatedAt, String createdBy, long version)`
  - `ScenarioRepository`:
    - `ScenarioRow create(String projectId, String name, String deterministicSettings, List<ScenarioStepInput> steps, String createdBy)`
    - `Optional<ScenarioRow> findById(String id)`
    - `List<ScenarioRow> findByProject(String projectId)`
    - `List<ScenarioRow> findByProjectPaged(String projectId, OffsetDateTime afterAt, String afterId, int limit)`
    - `Optional<ScenarioRow> update(String id, String name, String deterministicSettings, List<ScenarioStepInput> steps, long expectedVersion)` — `null` name/deterministicSettings/steps each mean "leave unchanged"; non-null `steps` replaces the whole list; always bumps `version`; `WHERE id AND version = expectedVersion`.
    - `boolean deleteById(String id)`

- [ ] **Step 1: Write the record types**

`ScenarioStepInput.java`:
```java
package com.ainclusive.iotsim.persistence.scenario;

/** Write-side step; {@code ordinal} is assigned by list position at persist time. */
public record ScenarioStepInput(String type, String targetSourceId, String params) {}
```

`ScenarioStepRow.java`:
```java
package com.ainclusive.iotsim.persistence.scenario;

/** Read-side projection of a {@code scenario_steps} row. */
public record ScenarioStepRow(int ordinal, String type, String targetSourceId, String params) {}
```

`ScenarioRow.java`:
```java
package com.ainclusive.iotsim.persistence.scenario;

import java.time.OffsetDateTime;
import java.util.List;

/** Persistence-level projection of a {@code scenarios} row plus its ordered steps. */
public record ScenarioRow(
        String id,
        String projectId,
        String name,
        String status,
        String deterministicSettings,
        List<ScenarioStepRow> steps,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        String createdBy,
        long version) {}
```

- [ ] **Step 2: Write the repository interface**

`ScenarioRepository.java`:
```java
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
```

- [ ] **Step 3: Write the failing repository IT**

`ScenarioRepositoryIT.java`:
```java
package com.ainclusive.iotsim.persistence.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.persistence.project.JooqProjectRepository;
import com.ainclusive.iotsim.persistence.project.ProjectRow;
import java.util.List;
import java.util.Optional;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Exercises {@link JooqScenarioRepository} (incl. ordered steps + JSONB) against real Postgres. */
@Testcontainers(disabledWithoutDocker = true)
class ScenarioRepositoryIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17");

    static ScenarioRepository scenarios;
    static String projectId;

    @BeforeAll
    static void migrateAndWire() {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        DSLContext dsl = DSL.using(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        ProjectRow project = new JooqProjectRepository(dsl).insert("Factory", null, "it");
        projectId = project.id();
        scenarios = new JooqScenarioRepository(dsl);
    }

    @Test
    void createPersistsScenarioAndOrderedSteps() {
        List<ScenarioStepInput> steps = List.of(
                new ScenarioStepInput("START", "ds-1", "{}"),
                new ScenarioStepInput("WAIT", null, "{\"ms\":500}"),
                new ScenarioStepInput("STOP", "ds-1", "{}"));

        ScenarioRow row = scenarios.create(projectId, "Flow", "{\"seed\":7}", steps, "it");

        assertThat(row.id()).isNotBlank();
        assertThat(row.status()).isEqualTo("DRAFT");
        assertThat(row.version()).isZero();
        assertThat(row.deterministicSettings()).contains("seed");
        assertThat(row.steps()).extracting(ScenarioStepRow::ordinal).containsExactly(0, 1, 2);
        assertThat(row.steps()).extracting(ScenarioStepRow::type).containsExactly("START", "WAIT", "STOP");
        assertThat(row.steps().get(1).params()).contains("ms");

        scenarios.deleteById(row.id());
    }

    @Test
    void createWithNullJsonFallsBackToEmptyObject() {
        ScenarioRow row = scenarios.create(projectId, "Empty", null, List.of(), "it");
        assertThat(row.deterministicSettings()).isEqualTo("{}");
        assertThat(row.steps()).isEmpty();
        scenarios.deleteById(row.id());
    }

    @Test
    void updateReplacesStepsAndBumpsVersionWhenVersionMatches() {
        ScenarioRow created = scenarios.create(projectId, "V", "{}",
                List.of(new ScenarioStepInput("MARKER", null, "{}")), "it");

        Optional<ScenarioRow> updated = scenarios.update(created.id(), "V2", null,
                List.of(new ScenarioStepInput("START", "ds-9", "{}"),
                        new ScenarioStepInput("STOP", "ds-9", "{}")),
                created.version());

        assertThat(updated).isPresent();
        assertThat(updated.get().name()).isEqualTo("V2");
        assertThat(updated.get().version()).isEqualTo(created.version() + 1);
        assertThat(updated.get().steps()).extracting(ScenarioStepRow::type)
                .containsExactly("START", "STOP");
        scenarios.deleteById(created.id());
    }

    @Test
    void updateWithNullStepsKeepsExistingSteps() {
        ScenarioRow created = scenarios.create(projectId, "Keep", "{}",
                List.of(new ScenarioStepInput("MARKER", null, "{}")), "it");

        Optional<ScenarioRow> updated = scenarios.update(created.id(), "KeepRenamed", null,
                null, created.version());

        assertThat(updated).isPresent();
        assertThat(updated.get().name()).isEqualTo("KeepRenamed");
        assertThat(updated.get().steps()).extracting(ScenarioStepRow::type).containsExactly("MARKER");
        scenarios.deleteById(created.id());
    }

    @Test
    void updateWithStaleVersionReturnsEmpty() {
        ScenarioRow created = scenarios.create(projectId, "Stale", "{}", List.of(), "it");
        assertThat(scenarios.update(created.id(), "X", null, null, created.version() + 99)).isEmpty();
        scenarios.deleteById(created.id());
    }

    @Test
    void findByProjectPagedNewestFirstAndDeleteCascadesSteps() {
        ScenarioRow a = scenarios.create(projectId, "A", "{}",
                List.of(new ScenarioStepInput("MARKER", null, "{}")), "it");
        ScenarioRow b = scenarios.create(projectId, "B", "{}", List.of(), "it");

        List<ScenarioRow> page1 = scenarios.findByProjectPaged(projectId, null, null, 1);
        assertThat(page1).hasSize(1);
        assertThat(page1.get(0).id()).isEqualTo(b.id());

        assertThat(scenarios.deleteById(a.id())).isTrue();
        assertThat(scenarios.findById(a.id())).isEmpty();
        scenarios.deleteById(b.id());
    }
}
```

- [ ] **Step 4: Run the IT to verify it fails**

Run (Colima env vars exported per project setup):
```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew :persistence:test --tests '*ScenarioRepositoryIT' --rerun-tasks
```
Expected: FAIL to compile / `JooqScenarioRepository` not found.

- [ ] **Step 5: Write the jOOQ implementation**

`JooqScenarioRepository.java`:
```java
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
```

- [ ] **Step 6: Run the IT to verify it passes**

Run:
```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew :persistence:test --tests '*ScenarioRepositoryIT' --rerun-tasks
```
Expected: PASS (6 tests).

- [ ] **Step 7: Commit**

```bash
git add persistence/src/main/java/com/ainclusive/iotsim/persistence/scenario \
        persistence/src/test/java/com/ainclusive/iotsim/persistence/scenario
git commit -m "feat(IS-085): scenario persistence (repository + steps)"
```

---

## Task 2: Domain layer (`ScenarioService`)

**Files:**
- Create: `domain/src/main/java/com/ainclusive/iotsim/domain/scenario/ScenarioStep.java`
- Create: `domain/src/main/java/com/ainclusive/iotsim/domain/scenario/Scenario.java`
- Create: `domain/src/main/java/com/ainclusive/iotsim/domain/scenario/ScenarioService.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/scenario/ScenarioServiceTest.java`

**Interfaces:**
- Consumes: `ScenarioRepository`, `ScenarioRow`, `ScenarioStepRow`, `ScenarioStepInput` (Task 1); `com.ainclusive.iotsim.persistence.project.ProjectRepository`; `com.ainclusive.iotsim.domain.common.ResourceNotFoundException`; `com.ainclusive.iotsim.domain.common.ConcurrencyConflictException`; `com.ainclusive.iotsim.domain.support.{Page,PageCursor}`; `tools.jackson.databind.ObjectMapper`.
- Produces:
  - `record ScenarioStep(int ordinal, String type, String targetSourceId, String params)`
  - `record Scenario(String id, String projectId, String name, String status, String deterministicSettings, List<ScenarioStep> steps, java.time.Instant createdAt, java.time.Instant updatedAt, String createdBy, long version)`
  - `ScenarioService` (`@Service`):
    - `Scenario create(String projectId, String name, String deterministicSettings, List<ScenarioStep> steps, String actor)`
    - `Page<Scenario> listPaged(String projectId, String cursor, Integer limit)`
    - `Scenario get(String projectId, String id)`
    - `Scenario update(String projectId, String id, String name, String deterministicSettings, List<ScenarioStep> steps, long expectedVersion)` — `null` field = leave unchanged
    - `Scenario duplicate(String projectId, String id, String actor)`
    - `void delete(String projectId, String id)`
  - Constant `Set<String> STEP_TYPES = {"START","STOP","REPLAY","SYNTHETIC","FAULT","WAIT","MARKER"}`
  - Incoming `ScenarioStep.ordinal` is ignored on write; the service normalizes by list position.

- [ ] **Step 1: Write the domain records**

`ScenarioStep.java`:
```java
package com.ainclusive.iotsim.domain.scenario;

/** One step in a scenario flow (backend-specs/03, 06). On write, {@code ordinal} is ignored
 *  and reassigned by list position. */
public record ScenarioStep(int ordinal, String type, String targetSourceId, String params) {}
```

`Scenario.java`:
```java
package com.ainclusive.iotsim.domain.scenario;

import java.time.Instant;
import java.util.List;

/** A test flow: an ordered list of typed steps (backend-specs/03). */
public record Scenario(
        String id,
        String projectId,
        String name,
        String status,
        String deterministicSettings,
        List<ScenarioStep> steps,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        long version) {}
```

- [ ] **Step 2: Write the failing service test**

`ScenarioServiceTest.java`:
```java
package com.ainclusive.iotsim.domain.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.project.ProjectRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepInput;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepRow;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ScenarioServiceTest {

    private static final String PROJECT = "proj-1";

    private ScenarioService service;

    @BeforeEach
    void setUp() {
        service = new ScenarioService(
                new InMemoryScenarioRepository(),
                new FakeProjectRepository(Set.of(PROJECT)),
                new ObjectMapper());
    }

    private static ScenarioStep step(String type, String target, String params) {
        return new ScenarioStep(0, type, target, params);
    }

    @Test
    void createUnderExistingProjectNormalizesOrdinalsAndIsDraft() {
        Scenario s = service.create(PROJECT, "Flow", "{}",
                List.of(step("START", "ds-1", "{}"), step("STOP", "ds-1", "{}")), "alice");
        assertThat(s.id()).isNotBlank();
        assertThat(s.status()).isEqualTo("DRAFT");
        assertThat(s.createdBy()).isEqualTo("alice");
        assertThat(s.steps()).extracting(ScenarioStep::ordinal).containsExactly(0, 1);
    }

    @Test
    void createUnderMissingProjectThrowsNotFound() {
        assertThatThrownBy(() -> service.create("nope", "X", "{}", List.of(), "a"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createWithBlankNameThrows() {
        assertThatThrownBy(() -> service.create(PROJECT, "  ", "{}", List.of(), "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithUnknownStepTypeThrows() {
        assertThatThrownBy(() -> service.create(PROJECT, "X", "{}", List.of(step("NOPE", null, "{}")), "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createStartStepWithoutTargetThrows() {
        assertThatThrownBy(() -> service.create(PROJECT, "X", "{}", List.of(step("START", null, "{}")), "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithNonObjectParamsThrows() {
        assertThatThrownBy(() -> service.create(PROJECT, "X", "{}", List.of(step("WAIT", null, "[1,2]")), "a"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createWithNullParamsDefaultsToEmptyObject() {
        Scenario s = service.create(PROJECT, "X", null, List.of(step("MARKER", null, null)), "a");
        assertThat(s.deterministicSettings()).isEqualTo("{}");
        assertThat(s.steps().get(0).params()).isEqualTo("{}");
    }

    @Test
    void getFromWrongProjectThrowsNotFound() {
        Scenario s = service.create(PROJECT, "Mine", "{}", List.of(), "a");
        assertThatThrownBy(() -> service.get("other", s.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void updateWithNullStepsKeepsStepsAndRenames() {
        Scenario s = service.create(PROJECT, "N", "{}", List.of(step("MARKER", null, "{}")), "a");
        Scenario u = service.update(PROJECT, s.id(), "N2", null, null, s.version());
        assertThat(u.name()).isEqualTo("N2");
        assertThat(u.steps()).extracting(ScenarioStep::type).containsExactly("MARKER");
        assertThat(u.version()).isEqualTo(s.version() + 1);
    }

    @Test
    void updateWithStaleVersionThrowsConflict() {
        Scenario s = service.create(PROJECT, "N", "{}", List.of(), "a");
        assertThatThrownBy(() -> service.update(PROJECT, s.id(), "X", null, null, s.version() + 5))
                .isInstanceOf(ConcurrencyConflictException.class);
    }

    @Test
    void updateMissingThrowsNotFound() {
        assertThatThrownBy(() -> service.update(PROJECT, "nope", "X", null, null, 0))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void duplicateCopiesStepsAsDraftWithSuffix() {
        Scenario s = service.create(PROJECT, "Orig", "{\"seed\":1}",
                List.of(step("START", "ds-1", "{}")), "a");
        Scenario copy = service.duplicate(PROJECT, s.id(), "bob");
        assertThat(copy.id()).isNotEqualTo(s.id());
        assertThat(copy.name()).isEqualTo("Orig (copy)");
        assertThat(copy.status()).isEqualTo("DRAFT");
        assertThat(copy.createdBy()).isEqualTo("bob");
        assertThat(copy.steps()).extracting(ScenarioStep::type).containsExactly("START");
        assertThat(copy.deterministicSettings()).contains("seed");
    }

    @Test
    void deleteRemovesScenario() {
        Scenario s = service.create(PROJECT, "D", "{}", List.of(), "a");
        service.delete(PROJECT, s.id());
        assertThatThrownBy(() -> service.get(PROJECT, s.id()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- fakes ----

    private static final class InMemoryScenarioRepository implements ScenarioRepository {
        private final Map<String, ScenarioRow> rows = new HashMap<>();
        private int seq;

        @Override
        public ScenarioRow create(String projectId, String name, String deterministicSettings,
                List<ScenarioStepInput> steps, String createdBy) {
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            ScenarioRow row = new ScenarioRow("scn-" + (++seq), projectId, name, "DRAFT",
                    deterministicSettings != null ? deterministicSettings : "{}",
                    toRows(steps), now, now, createdBy, 0);
            rows.put(row.id(), row);
            return row;
        }

        @Override
        public Optional<ScenarioRow> findById(String id) {
            return Optional.ofNullable(rows.get(id));
        }

        @Override
        public List<ScenarioRow> findByProject(String projectId) {
            return rows.values().stream().filter(r -> r.projectId().equals(projectId)).toList();
        }

        @Override
        public List<ScenarioRow> findByProjectPaged(String projectId,
                OffsetDateTime afterAt, String afterId, int limit) {
            return findByProject(projectId).stream().limit(limit).toList();
        }

        @Override
        public Optional<ScenarioRow> update(String id, String name, String deterministicSettings,
                List<ScenarioStepInput> steps, long expectedVersion) {
            ScenarioRow cur = rows.get(id);
            if (cur == null || cur.version() != expectedVersion) {
                return Optional.empty();
            }
            ScenarioRow next = new ScenarioRow(cur.id(), cur.projectId(),
                    name != null ? name : cur.name(), cur.status(),
                    deterministicSettings != null ? deterministicSettings : cur.deterministicSettings(),
                    steps != null ? toRows(steps) : cur.steps(),
                    cur.createdAt(), OffsetDateTime.now(ZoneOffset.UTC), cur.createdBy(),
                    cur.version() + 1);
            rows.put(id, next);
            return Optional.of(next);
        }

        @Override
        public boolean deleteById(String id) {
            return rows.remove(id) != null;
        }

        private static List<ScenarioStepRow> toRows(List<ScenarioStepInput> steps) {
            List<ScenarioStepRow> out = new ArrayList<>();
            for (int i = 0; i < steps.size(); i++) {
                ScenarioStepInput s = steps.get(i);
                out.add(new ScenarioStepRow(i, s.type(), s.targetSourceId(),
                        s.params() != null ? s.params() : "{}"));
            }
            return out;
        }
    }

    private static final class FakeProjectRepository implements ProjectRepository {
        private final Set<String> existing;

        FakeProjectRepository(Set<String> existing) {
            this.existing = existing;
        }

        @Override
        public Optional<ProjectRow> findById(String id) {
            if (!existing.contains(id)) {
                return Optional.empty();
            }
            OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
            return Optional.of(new ProjectRow(id, "p", null, "ACTIVE", now, now, "local", 0));
        }

        @Override public ProjectRow insert(String n, String d, String by) { throw new UnsupportedOperationException(); }
        @Override public List<ProjectRow> findAll() { return List.of(); }
        @Override public List<ProjectRow> findAllPaged(String s, OffsetDateTime a, String i, int l) { return List.of(); }
        @Override public Optional<ProjectRow> update(String i, String n, String d, long v) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectRow> archive(String i) { throw new UnsupportedOperationException(); }
        @Override public boolean deleteById(String i) { throw new UnsupportedOperationException(); }
    }
}
```

- [ ] **Step 3: Run the test to verify it fails**

Run:
```bash
./gradlew :domain:test --tests '*ScenarioServiceTest'
```
Expected: FAIL to compile — `ScenarioService` not found.

- [ ] **Step 4: Write the service**

`ScenarioService.java`:
```java
package com.ainclusive.iotsim.domain.scenario;

import com.ainclusive.iotsim.domain.common.ConcurrencyConflictException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.support.Page;
import com.ainclusive.iotsim.domain.support.PageCursor;
import com.ainclusive.iotsim.persistence.project.ProjectRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepInput;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepRow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Scenario authoring CRUD (IS-085). Validation is model-level only; cross-entity checks
 *  and run execution belong to IS-086. */
@Service
public class ScenarioService {

    static final Set<String> STEP_TYPES =
            Set.of("START", "STOP", "REPLAY", "SYNTHETIC", "FAULT", "WAIT", "MARKER");
    private static final Set<String> TARGET_REQUIRED = Set.of("START", "STOP");

    private final ScenarioRepository scenarios;
    private final ProjectRepository projects;
    private final ObjectMapper json;

    public ScenarioService(ScenarioRepository scenarios, ProjectRepository projects, ObjectMapper json) {
        this.scenarios = scenarios;
        this.projects = projects;
        this.json = json;
    }

    public Scenario create(String projectId, String name, String deterministicSettings,
            List<ScenarioStep> steps, String actor) {
        requireProject(projectId);
        requireName(name);
        String det = normalizeJsonObject(deterministicSettings, "deterministicSettings");
        List<ScenarioStepInput> inputs = validateAndMap(steps);
        return map(scenarios.create(projectId, name, det, inputs, actor));
    }

    public Page<Scenario> listPaged(String projectId, String cursor, Integer limit) {
        requireProject(projectId);
        int size = PageCursor.clamp(limit);
        PageCursor.Parts after = PageCursor.decode(cursor);
        OffsetDateTime afterAt = after != null ? after.at() : null;
        String afterId = after != null ? after.id() : null;
        List<ScenarioRow> rows = scenarios.findByProjectPaged(projectId, afterAt, afterId, size + 1);
        String nextCursor = null;
        if (rows.size() > size) {
            rows = rows.subList(0, size);
            ScenarioRow last = rows.get(rows.size() - 1);
            nextCursor = PageCursor.encode(last.createdAt(), last.id());
        }
        return new Page<>(rows.stream().map(this::map).toList(), nextCursor, size);
    }

    public Scenario get(String projectId, String id) {
        return map(requireScenario(projectId, id));
    }

    public Scenario update(String projectId, String id, String name, String deterministicSettings,
            List<ScenarioStep> steps, long expectedVersion) {
        requireScenario(projectId, id);
        String validatedName = null;
        if (name != null) {
            requireName(name);
            validatedName = name;
        }
        String det = deterministicSettings != null
                ? normalizeJsonObject(deterministicSettings, "deterministicSettings")
                : null;
        List<ScenarioStepInput> inputs = steps != null ? validateAndMap(steps) : null;
        return scenarios.update(id, validatedName, det, inputs, expectedVersion)
                .map(this::map)
                .orElseThrow(() -> new ConcurrencyConflictException("Scenario", id, expectedVersion));
    }

    public Scenario duplicate(String projectId, String id, String actor) {
        ScenarioRow src = requireScenario(projectId, id);
        List<ScenarioStepInput> copied = src.steps().stream()
                .map(s -> new ScenarioStepInput(s.type(), s.targetSourceId(), s.params()))
                .toList();
        return map(scenarios.create(projectId, src.name() + " (copy)",
                src.deterministicSettings(), copied, actor));
    }

    public void delete(String projectId, String id) {
        requireScenario(projectId, id);
        scenarios.deleteById(id);
    }

    // ---- helpers ----

    private void requireProject(String projectId) {
        if (projects.findById(projectId).isEmpty()) {
            throw new ResourceNotFoundException("Project", projectId);
        }
    }

    private ScenarioRow requireScenario(String projectId, String id) {
        return scenarios.findById(id)
                .filter(r -> r.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Scenario", id));
    }

    private static void requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
    }

    private List<ScenarioStepInput> validateAndMap(List<ScenarioStep> steps) {
        if (steps == null) {
            return List.of();
        }
        return steps.stream().map(s -> {
            if (s.type() == null || !STEP_TYPES.contains(s.type())) {
                throw new IllegalArgumentException("invalid step type: " + s.type());
            }
            if (TARGET_REQUIRED.contains(s.type()) && (s.targetSourceId() == null || s.targetSourceId().isBlank())) {
                throw new IllegalArgumentException(s.type() + " step requires targetSourceId");
            }
            String params = normalizeJsonObject(s.params(), "step params");
            return new ScenarioStepInput(s.type(), s.targetSourceId(), params);
        }).toList();
    }

    /** Ensures the value is a JSON object; null/blank → "{}". Throws 400 on invalid/non-object JSON. */
    private String normalizeJsonObject(String value, String field) {
        if (value == null || value.isBlank()) {
            return "{}";
        }
        try {
            JsonNode node = json.readTree(value);
            if (!node.isObject()) {
                throw new IllegalArgumentException(field + " must be a JSON object");
            }
        } catch (JacksonException e) {
            throw new IllegalArgumentException(field + " must be valid JSON: " + e.getOriginalMessage());
        }
        return value;
    }

    private Scenario map(ScenarioRow r) {
        List<ScenarioStep> steps = r.steps().stream()
                .map(s -> new ScenarioStep(s.ordinal(), s.type(), s.targetSourceId(), s.params()))
                .toList();
        return new Scenario(r.id(), r.projectId(), r.name(), r.status(), r.deterministicSettings(),
                steps, r.createdAt().toInstant(), r.updatedAt().toInstant(), r.createdBy(), r.version());
    }
}
```

> Note: confirm the `JacksonException` accessor name. If `getOriginalMessage()` does not exist on this Jackson 3 version, use `e.getMessage()`. Verify with the existing usage in the codebase (`grep -rn "JacksonException" domain/src/main api/src/main`).

- [ ] **Step 5: Run the test to verify it passes**

Run:
```bash
./gradlew :domain:test --tests '*ScenarioServiceTest'
```
Expected: PASS (all tests).

- [ ] **Step 6: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/scenario \
        domain/src/test/java/com/ainclusive/iotsim/domain/scenario
git commit -m "feat(IS-085): scenario domain service (CRUD + duplicate + validation)"
```

---

## Task 3: API layer (`ScenarioController`)

**Files:**
- Create: `api/src/main/java/com/ainclusive/iotsim/api/scenario/ScenarioController.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/scenario/ScenarioControllerTest.java`

**Interfaces:**
- Consumes: `com.ainclusive.iotsim.domain.scenario.{Scenario,ScenarioStep,ScenarioService}`; `com.ainclusive.iotsim.domain.support.Page`; `com.ainclusive.iotsim.api.error.PreconditionRequiredException`.
- Produces: REST endpoints under `/api/v1/projects/{projectId}/scenarios`.
- Test approach: a **POJO controller unit test** (the repo convention — see `RuntimeStreamControllerTest`): instantiate `ScenarioController` with a capturing `ScenarioService` subclass, call methods, assert on `ResponseEntity` status/headers and pre-delegation logic. No new test dependencies. True HTTP-layer (`MockMvc`) coverage — status mapping through `GlobalExceptionHandler`, JSON (de)serialization — is **out of scope here** and tracked separately as **IS-121 [SDLC]** (introduce the web-layer/MockMvc test harness repo-wide).

- [ ] **Step 1: Write the controller**

`ScenarioController.java`:
```java
package com.ainclusive.iotsim.api.scenario;

import com.ainclusive.iotsim.api.error.PreconditionRequiredException;
import com.ainclusive.iotsim.domain.scenario.Scenario;
import com.ainclusive.iotsim.domain.scenario.ScenarioService;
import com.ainclusive.iotsim.domain.scenario.ScenarioStep;
import com.ainclusive.iotsim.domain.support.Page;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Scenario authoring CRUD within a project (backend-specs/05_API_CONTRACT.md, IS-085). */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/scenarios")
public class ScenarioController {

    private final ScenarioService scenarios;

    public ScenarioController(ScenarioService scenarios) {
        this.scenarios = scenarios;
    }

    @GetMapping
    public Page<ScenarioResponse> list(
            @PathVariable String projectId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit) {
        return scenarios.listPaged(projectId, cursor, limit).map(ScenarioResponse::from);
    }

    @PostMapping
    public ResponseEntity<ScenarioResponse> create(
            @PathVariable String projectId, @RequestBody CreateScenarioRequest req) {
        if (req == null || req.name() == null || req.name().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        Scenario s = scenarios.create(projectId, req.name(), req.deterministicSettings(),
                toSteps(req.steps()), "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/scenarios/" + s.id()))
                .eTag(etag(s.version()))
                .body(ScenarioResponse.from(s));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScenarioResponse> get(@PathVariable String projectId, @PathVariable String id) {
        Scenario s = scenarios.get(projectId, id);
        return ResponseEntity.ok().eTag(etag(s.version())).body(ScenarioResponse.from(s));
    }

    @PatchMapping("/{id}")
    public ResponseEntity<ScenarioResponse> update(
            @PathVariable String projectId,
            @PathVariable String id,
            @RequestHeader(value = "If-Match", required = false) String ifMatch,
            @RequestBody UpdateScenarioRequest req) {
        if (ifMatch == null || ifMatch.isBlank()) {
            throw new PreconditionRequiredException("If-Match header with the current version is required");
        }
        List<ScenarioStep> steps = req != null && req.steps() != null ? toSteps(req.steps()) : null;
        Scenario s = scenarios.update(projectId, id,
                req != null ? req.name() : null,
                req != null ? req.deterministicSettings() : null,
                steps, parseVersion(ifMatch));
        return ResponseEntity.ok().eTag(etag(s.version())).body(ScenarioResponse.from(s));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String projectId, @PathVariable String id) {
        scenarios.delete(projectId, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/duplicate")
    public ResponseEntity<ScenarioResponse> duplicate(
            @PathVariable String projectId, @PathVariable String id) {
        Scenario s = scenarios.duplicate(projectId, id, "local");
        return ResponseEntity.created(
                        URI.create("/api/v1/projects/" + projectId + "/scenarios/" + s.id()))
                .eTag(etag(s.version()))
                .body(ScenarioResponse.from(s));
    }

    private static List<ScenarioStep> toSteps(List<StepDto> dtos) {
        if (dtos == null) {
            return List.of();
        }
        List<ScenarioStep> out = new java.util.ArrayList<>();
        for (int i = 0; i < dtos.size(); i++) {
            StepDto d = dtos.get(i);
            out.add(new ScenarioStep(i, d.type(), d.targetSourceId(), d.params()));
        }
        return out;
    }

    private static String etag(long version) {
        return "\"" + version + "\"";
    }

    private static long parseVersion(String ifMatch) {
        String v = ifMatch.trim();
        if (v.startsWith("W/")) {
            v = v.substring(2);
        }
        v = v.replace("\"", "").trim();
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid If-Match version: " + ifMatch);
        }
    }

    public record StepDto(String type, String targetSourceId, String params) {}

    public record CreateScenarioRequest(
            String name, String deterministicSettings, List<StepDto> steps) {}

    public record UpdateScenarioRequest(
            String name, String deterministicSettings, List<StepDto> steps) {}

    public record StepResponse(int ordinal, String type, String targetSourceId, String params) {
        static StepResponse from(ScenarioStep s) {
            return new StepResponse(s.ordinal(), s.type(), s.targetSourceId(), s.params());
        }
    }

    public record ScenarioResponse(
            String id,
            String projectId,
            String name,
            String status,
            String deterministicSettings,
            List<StepResponse> steps,
            Instant createdAt,
            Instant updatedAt,
            String createdBy,
            long version) {

        static ScenarioResponse from(Scenario s) {
            return new ScenarioResponse(
                    s.id(), s.projectId(), s.name(), s.status(), s.deterministicSettings(),
                    s.steps().stream().map(StepResponse::from).toList(),
                    s.createdAt(), s.updatedAt(), s.createdBy(), s.version());
        }
    }
}
```

- [ ] **Step 2: Write the controller unit test**

`ScenarioControllerTest.java`:
```java
package com.ainclusive.iotsim.api.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ainclusive.iotsim.api.error.PreconditionRequiredException;
import com.ainclusive.iotsim.api.scenario.ScenarioController.CreateScenarioRequest;
import com.ainclusive.iotsim.api.scenario.ScenarioController.ScenarioResponse;
import com.ainclusive.iotsim.api.scenario.ScenarioController.StepDto;
import com.ainclusive.iotsim.api.scenario.ScenarioController.UpdateScenarioRequest;
import com.ainclusive.iotsim.domain.scenario.Scenario;
import com.ainclusive.iotsim.domain.scenario.ScenarioService;
import com.ainclusive.iotsim.domain.scenario.ScenarioStep;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/** POJO controller unit test (repo convention, cf. RuntimeStreamControllerTest): asserts
 *  status/headers and pre-delegation logic. HTTP-layer mapping is IS-121. */
class ScenarioControllerTest {

    private static Scenario sample(long version) {
        return new Scenario("scn-1", "p1", "Flow", "DRAFT", "{}",
                List.of(new ScenarioStep(0, "MARKER", null, "{}")),
                Instant.EPOCH, Instant.EPOCH, "local", version);
    }

    /** Capturing fake: overrides every method used; super-ctor args are unused. */
    private static final class FakeService extends ScenarioService {
        List<ScenarioStep> createdSteps;
        long updatedVersion = -1;
        boolean deleted;

        FakeService() {
            super(null, null, null);
        }

        @Override
        public Scenario create(String p, String n, String d, List<ScenarioStep> steps, String a) {
            this.createdSteps = steps;
            return sample(0);
        }

        @Override
        public Scenario get(String p, String id) {
            return sample(3);
        }

        @Override
        public Scenario update(String p, String id, String n, String d, List<ScenarioStep> steps, long ev) {
            this.updatedVersion = ev;
            return sample(ev + 1);
        }

        @Override
        public Scenario duplicate(String p, String id, String a) {
            return sample(0);
        }

        @Override
        public void delete(String p, String id) {
            this.deleted = true;
        }
    }

    @Test
    void createReturns201WithEtagLocationAndNormalizedOrdinals() {
        FakeService svc = new FakeService();
        var req = new CreateScenarioRequest("Flow", "{}",
                List.of(new StepDto("START", "ds-1", "{}"), new StepDto("STOP", "ds-1", "{}")));

        ResponseEntity<ScenarioResponse> resp = new ScenarioController(svc).create("p1", req);

        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"0\"");
        assertThat(resp.getHeaders().getLocation()).hasToString("/api/v1/projects/p1/scenarios/scn-1");
        assertThat(svc.createdSteps).extracting(ScenarioStep::ordinal).containsExactly(0, 1);
    }

    @Test
    void createWithBlankNameThrows() {
        ScenarioController c = new ScenarioController(new FakeService());
        assertThatThrownBy(() -> c.create("p1", new CreateScenarioRequest("  ", "{}", List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void getReturnsEtagFromVersion() {
        ResponseEntity<ScenarioResponse> resp = new ScenarioController(new FakeService()).get("p1", "scn-1");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"3\"");
    }

    @Test
    void updateWithoutIfMatchThrowsPreconditionRequired() {
        ScenarioController c = new ScenarioController(new FakeService());
        assertThatThrownBy(() -> c.update("p1", "scn-1", null, new UpdateScenarioRequest("X", null, null)))
                .isInstanceOf(PreconditionRequiredException.class);
    }

    @Test
    void updateParsesIfMatchVersionAndReturnsBumpedEtag() {
        FakeService svc = new FakeService();
        ResponseEntity<ScenarioResponse> resp = new ScenarioController(svc)
                .update("p1", "scn-1", "\"5\"", new UpdateScenarioRequest("X", null, null));
        assertThat(svc.updatedVersion).isEqualTo(5);
        assertThat(resp.getHeaders().getETag()).isEqualTo("\"6\"");
    }

    @Test
    void updateWithNonNumericIfMatchThrows() {
        ScenarioController c = new ScenarioController(new FakeService());
        assertThatThrownBy(() -> c.update("p1", "scn-1", "nope", new UpdateScenarioRequest("X", null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void deleteReturns204() {
        FakeService svc = new FakeService();
        ResponseEntity<Void> resp = new ScenarioController(svc).delete("p1", "scn-1");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(svc.deleted).isTrue();
    }

    @Test
    void duplicateReturns201WithLocation() {
        ResponseEntity<ScenarioResponse> resp = new ScenarioController(new FakeService()).duplicate("p1", "scn-1");
        assertThat(resp.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(resp.getHeaders().getLocation()).isNotNull();
    }
}
```

- [ ] **Step 3: Run the controller test**

Run:
```bash
./gradlew :api:test --tests '*ScenarioControllerTest'
```
Expected: PASS (8 tests). (Requires `ScenarioController` to be non-`final` and its methods overridable — the default for the class as written.)

- [ ] **Step 4: Build the api module**

Run:
```bash
./gradlew :api:test
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/scenario \
        api/src/test/java/com/ainclusive/iotsim/api/scenario
git commit -m "feat(IS-085): scenario REST controller (CRUD + duplicate) + controller unit test"
```

---

## Task 4: Full verification

- [ ] **Step 1: Run the full build** ([[always-compile-and-test]])

Run (with Colima env so ITs do not silently skip):
```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew build --rerun-tasks
```
Expected: BUILD SUCCESSFUL; `ScenarioRepositoryIT` and `ScenarioServiceTest` run and pass; the context-boot smoke IT boots with the new `ScenarioController`/`ScenarioService`/`JooqScenarioRepository` beans.

- [ ] **Step 2: Confirm no skipped ITs**

Verify `ScenarioRepositoryIT` shows as executed (not skipped) in the test report. If skipped, re-export the Colima Docker env vars and re-run.

- [ ] **Step 3: Open the PR**

Hand off to the `/open-pr` skill — it flips the `IS-085` checkbox in `backend-specs/TASKS.md` in the same PR (CI catalog-sync gate), creates the PR with `Implements: IS-085`, arms squash auto-merge, and moves the board to In review. Do not flip the catalog checkbox manually here.

---

## Self-Review

**Spec coverage:**
- Scenario model + steps → Tasks 1–2 (records, repo, service). ✅
- CRUD + duplicate REST (`GET/POST/PATCH/DELETE /scenarios`, `/duplicate`) → Task 3, with a POJO controller unit test (`ScenarioControllerTest`). HTTP-layer/MockMvc coverage deferred to IS-121. ✅
- `status` always DRAFT → repo never sets status on create/update; column default `DRAFT`. ✅
- params opaque JSON passthrough + model-level checks (type set, START/STOP target, JSON object) → `ScenarioService.validateAndMap`/`normalizeJsonObject`. ✅
- Cursor pagination (IS-074) → `listPaged` + `findByProjectPaged`. ✅
- Optimistic concurrency (ETag/If-Match) → controller `parseVersion` + service conflict mapping + repo `WHERE version=expectedVersion`. ✅
- Out-of-scope items (validate/run/READY/INVALID/cross-entity/audit/UI) → not implemented. ✅

**Placeholder scan:** No TBD/TODO. One verification note on the `JacksonException` accessor (Step 4, Task 2) — an explicit verify-or-swap instruction, not a placeholder.

**Type consistency:** `ScenarioStepInput(type,targetSourceId,params)`, `ScenarioStepRow(ordinal,type,targetSourceId,params)`, `ScenarioRow(...,List<ScenarioStepRow> steps,...)`, domain `ScenarioStep(ordinal,type,targetSourceId,params)`, `Scenario(...)` — used consistently across repo, service, controller, and both test doubles. Service method signatures match the controller call sites and the `ScenarioServiceTest` calls. ✅
