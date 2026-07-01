# IS-086 Scenario validation + run execution — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make scenarios runnable — `GET /scenarios/{id}/validate` (compute + persist READY/INVALID) and `POST /scenarios/{id}/run` (execute ordered steps as a `SCENARIO` run that orchestrates the existing replay/synthetic/runtime services, with `REPLAY`/`SYNTHETIC` steps opening child runs linked via a new `runs.parent_run_id`).

**Architecture:** Two new domain services (`ScenarioValidationService`, `ScenarioRunService`) beside the IS-085 `ScenarioService`. Run execution reuses `ReplayService`/`SyntheticRunService` (extended with a `parentRunId` overload) and `DataSourceService` for START/STOP; WAIT is a bounded sleep, MARKER a runtime event. A Flyway migration adds `runs.parent_run_id`; jOOQ regenerates from it. Synchronous, sequential, fail-fast — mirrors the existing bounded replay/synthetic model.

**Tech Stack:** Java 21, Spring Boot, jOOQ, Jackson 3 (`tools.jackson.*`), JUnit 5 + AssertJ + Mockito (available in `domain`), Testcontainers (Postgres 17), Flyway.

## Global Constraints

- Jackson 3: `tools.jackson.databind.ObjectMapper`, `tools.jackson.databind.JsonNode`, `tools.jackson.core.JacksonException` (unchecked). NEVER `com.fasterxml.jackson.*`.
- `@Service`/`@Repository`, constructor injection only (single ctor).
- Run states: `QUEUED → RUNNING → STOPPED|FAILED|COMPLETED`. Run kinds: `REPLAY|SYNTHETIC|SCENARIO|RECORDING`. Trigger: `MANUAL|AUTOMATED`; initiator never anonymous (default `"local"`).
- Step types: `START, STOP, REPLAY, SYNTHETIC, FAULT, WAIT, MARKER`.
- Run model: a scenario run is ONE `SCENARIO` parent run; REPLAY/SYNTHETIC steps open child runs linked via `runs.parent_run_id`.
- FAULT: structurally valid → validation `WARNING` (not INVALID); `POST /run` rejects any FAULT-containing scenario with **501** before creating a run.
- `GET /validate` persists the computed `status` (READY/INVALID) to the scenario row (bumps version).
- Validation `status = INVALID` iff any ERROR issue; else READY. Empty scenario (0 steps) → INVALID.
- Execution: synchronous, sequential, **fail-fast** (exception ends parent run FAILED, remaining steps skipped).
- WAIT: bounded real sleep of `params.durationMs`, capped at `60_000` ms.
- Generated jOOQ stays under `build/` (regenerated from migrations; never committed). New Flyway migration uses the next free version (current max is `V7`).
- After all tasks: `./gradlew build` green. Catalog checkbox flip + board move happen via `/open-pr`, not this plan.

---

## File Structure

```
persistence/src/main/resources/db/migration/
  V8__runs_parent_run_id.sql                (create) — adds runs.parent_run_id + index
persistence/src/main/java/com/ainclusive/iotsim/persistence/run/
  RunRepository.java   (modify) — 7-arg create overload (+ 6-arg default delegating null)
  RunRow.java          (modify) — append String parentRunId
  JooqRunRepository.java (modify) — set/read PARENT_RUN_ID
persistence/src/main/java/com/ainclusive/iotsim/persistence/scenario/
  ScenarioRepository.java  (modify) — add updateStatus(id, status) → Optional<ScenarioRow>
  JooqScenarioRepository.java (modify) — implement updateStatus
domain/src/main/java/com/ainclusive/iotsim/domain/common/
  FeatureNotAvailableException.java (create) — → 501
  ScenarioInvalidException.java     (create) — → 422, carries issues
domain/src/main/java/com/ainclusive/iotsim/domain/replay/ReplayService.java     (modify) — parentRunId overload
domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunService.java (modify) — parentRunId overload
domain/src/main/java/com/ainclusive/iotsim/domain/scenario/
  ValidationIssue.java        (create)
  ScenarioValidation.java     (create)
  ScenarioValidationService.java (create)
  StepOutcome.java            (create)
  ScenarioRunSummary.java     (create)
  ScenarioRunService.java     (create)
api/src/main/java/com/ainclusive/iotsim/api/error/GlobalExceptionHandler.java (modify) — map the 2 new exceptions
api/src/main/java/com/ainclusive/iotsim/api/scenario/ScenarioController.java  (modify) — /validate + /run
```

---

## Task 1: Persistence — `runs.parent_run_id`

**Files:**
- Create: `persistence/src/main/resources/db/migration/V8__runs_parent_run_id.sql`
- Modify: `persistence/src/main/java/com/ainclusive/iotsim/persistence/run/RunRow.java`
- Modify: `persistence/src/main/java/com/ainclusive/iotsim/persistence/run/RunRepository.java`
- Modify: `persistence/src/main/java/com/ainclusive/iotsim/persistence/run/JooqRunRepository.java`
- Modify (fix compile): `domain/src/test/java/com/ainclusive/iotsim/domain/replay/ReplayServiceTest.java`, `domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunServiceTest.java`, `domain/src/test/java/com/ainclusive/iotsim/domain/evidence/EvidenceServiceTest.java`
- Test: `persistence/src/test/java/com/ainclusive/iotsim/persistence/run/RunRepositoryIT.java`

**Interfaces:**
- Produces: `RunRow` gains a trailing `String parentRunId`. `RunRepository.create(projectId, kind, trigger, initiator, List<String> sourceIds, scenarioId, String parentRunId)` (7-arg, primary); the 6-arg `create` becomes a `default` delegating with `parentRunId=null`.

- [ ] **Step 1: Verify V8 is free, write the migration**

Run: `ls persistence/src/main/resources/db/migration/` — confirm no `V8__`. If `V8__` is taken, use the next free `V<n>__` and adjust the filename below.

Create `V8__runs_parent_run_id.sql`:
```sql
-- Parent link for scenario runs: a SCENARIO run's REPLAY/SYNTHETIC steps open child
-- runs that point back at the parent (IS-086). See backend-specs/03 & 04.
alter table runs
    add column parent_run_id varchar references runs (id) on delete cascade;

create index runs_parent_run_id_idx on runs (parent_run_id);
```

- [ ] **Step 2: Regenerate jOOQ**

Run: `./gradlew :persistence:generateJooq`
Expected: BUILD SUCCESSFUL; `persistence/build/generated/jooq/.../tables/Runs.java` now has a `PARENT_RUN_ID` field.

- [ ] **Step 3: Add `parentRunId` to `RunRow` (trailing field)**

In `RunRow.java`, append the field after `sourceIds`:
```java
public record RunRow(
        String id,
        String projectId,
        String kind,
        String trigger,
        String initiator,
        String state,
        String scenarioId,
        String evidenceId,
        OffsetDateTime startedAt,
        OffsetDateTime endedAt,
        OffsetDateTime createdAt,
        List<String> sourceIds,
        String parentRunId) {
}
```

- [ ] **Step 4: Add the 7-arg `create` to `RunRepository`**

Replace the existing `create` declaration with:
```java
    /**
     * Creates a run in {@code QUEUED} state and records its participating sources.
     * {@code trigger}/{@code initiator} may be {@code null} (fall through to column
     * defaults {@code MANUAL}/{@code local}); {@code scenarioId} and
     * {@code parentRunId} are nullable; {@code sourceIds} may be empty.
     */
    RunRow create(String projectId, String kind, String trigger, String initiator,
            List<String> sourceIds, String scenarioId, String parentRunId);

    /** Backwards-compatible create for standalone (non-child) runs: {@code parentRunId = null}. */
    default RunRow create(String projectId, String kind, String trigger, String initiator,
            List<String> sourceIds, String scenarioId) {
        return create(projectId, kind, trigger, initiator, sourceIds, scenarioId, null);
    }
```

- [ ] **Step 5: Write the failing IT for parent linkage**

Add to `RunRepositoryIT.java` (mirror the existing test style; the file already wires `runs` + a `projectId` + at least one data source id — reuse them; if it lacks a helper to make a source, create the run with empty `sourceIds`):
```java
    @Test
    void childRunCarriesParentRunId() {
        RunRow parent = runs.create(projectId, "SCENARIO", "MANUAL", "local", List.of(), "scn-x", null);
        RunRow child = runs.create(projectId, "REPLAY", "MANUAL", "local", List.of(), null, parent.id());

        assertThat(parent.parentRunId()).isNull();
        assertThat(child.parentRunId()).isEqualTo(parent.id());
        assertThat(runs.findById(child.id())).get()
                .extracting(RunRow::parentRunId).isEqualTo(parent.id());
    }
```
(If `RunRepositoryIT` passes non-null `scenarioId` values that need a real `scenarios` row due to an FK, use `null` for `scenarioId` here — `runs.scenario_id` is `ON DELETE SET NULL` and nullable, so `null` is safe.)

- [ ] **Step 6: Run the IT to verify it fails**

Run:
```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew :persistence:test --tests '*RunRepositoryIT' --rerun-tasks
```
Expected: FAIL to compile (`RunRow` has no `parentRunId` / `create` arity) or assertion failure.

- [ ] **Step 7: Implement in `JooqRunRepository`**

Import the field is already `RUNS.*`. Replace the `create` method and the `map` methods:
```java
    @Override
    public RunRow create(String projectId, String kind, String trigger, String initiator,
            List<String> sourceIds, String scenarioId, String parentRunId) {
        String id = Ids.newId();
        List<String> sources = sourceIds == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(sourceIds));
        dsl.transaction(cfg -> {
            DSLContext tx = cfg.dsl();
            InsertSetMoreStep<RunsRecord> insert = tx.insertInto(RUNS)
                    .set(RUNS.ID, id)
                    .set(RUNS.PROJECT_ID, projectId)
                    .set(RUNS.KIND, kind)
                    .set(RUNS.SCENARIO_ID, scenarioId)
                    .set(RUNS.PARENT_RUN_ID, parentRunId);
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
```
Update BOTH `map` overloads to pass `r.getParentRunId()` as the trailing `RunRow` arg:
```java
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
```

- [ ] **Step 8: Fix the three test construction sites**

In each of `ReplayServiceTest.java`, `SyntheticRunServiceTest.java`, `EvidenceServiceTest.java`:
- Any `new RunRow(...)` literal: append a trailing `null` argument (the `parentRunId`).
- Any `implements RunRepository` fake (`FakeRuns` in `ReplayServiceTest`, and the fake in `EvidenceServiceTest`): change its `create(...)` override to the 7-arg signature `create(String projectId, String kind, String trigger, String initiator, List<String> sourceIds, String scenarioId, String parentRunId)` and store/use `parentRunId` when it builds the `RunRow` (pass it through as the trailing field). `SyntheticRunServiceTest` mocks `RunRepository` via Mockito — no override needed there, but its `new RunRow(...)` stubs need the trailing `null`.

Run each module's affected tests to confirm they compile and pass:
```bash
./gradlew :domain:test --tests '*ReplayServiceTest' --tests '*SyntheticRunServiceTest' --tests '*EvidenceServiceTest'
```
Expected: PASS.

- [ ] **Step 9: Run the IT to verify it passes**

Run:
```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew :persistence:test --tests '*RunRepositoryIT' --rerun-tasks
```
Expected: PASS incl. `childRunCarriesParentRunId`.

- [ ] **Step 10: Commit**

```bash
git add persistence/src/main/resources/db/migration/V8__runs_parent_run_id.sql \
        persistence/src/main/java/com/ainclusive/iotsim/persistence/run \
        persistence/src/test/java/com/ainclusive/iotsim/persistence/run/RunRepositoryIT.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/replay/ReplayServiceTest.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunServiceTest.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/evidence/EvidenceServiceTest.java
git commit -m "feat(IS-086): runs.parent_run_id (migration + repo + child linkage)"
```

---

## Task 2: `parentRunId` overloads on ReplayService & SyntheticRunService

**Files:**
- Modify: `domain/src/main/java/com/ainclusive/iotsim/domain/replay/ReplayService.java`
- Modify: `domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunService.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/replay/ReplayServiceTest.java`, `domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunServiceTest.java`

**Interfaces:**
- Consumes: `RunRepository.create(...parentRunId)` (Task 1).
- Produces:
  - `ReplayService.replay(String projectId, String dataSourceId, String recordingId, DeterministicSettings deterministicSettings, boolean compatibilityAck, String parentRunId) -> ReplaySummary`; the existing 5-arg `replay(...)` delegates with `parentRunId = null`.
  - `SyntheticRunService.run(String projectId, String dataSourceId, long durationMs, String parentRunId) -> SyntheticRunSummary`; the existing 3-arg `run(...)` delegates with `parentRunId = null`.

- [ ] **Step 1: Write failing tests for parent linkage**

In `ReplayServiceTest.java`, add (the `FakeRuns` fake now records `parentRunId` per Task 1 — expose it, e.g. a `Map<String,String> parentById` or capture the last created run's parent):
```java
    @Test
    void replayWithParentRunIdLinksChildRun() {
        ReplayService service = /* same wiring as the happy-path test */ replayService(List.of(sampleValue()));
        ReplaySummary summary = service.replay(PROJECT, SOURCE, RECORDING, null, false, "parent-run-1");
        assertThat(runs.parentOf(summary.runId())).isEqualTo("parent-run-1");
    }
```
Add a `parentOf(String runId)` accessor to `FakeRuns` that returns the stored `parentRunId` for a run.

In `SyntheticRunServiceTest.java` (Mockito), add:
```java
    @Test
    void runWithParentRunIdPassesItToRunCreate() {
        // arrange the same happy-path stubs as the existing success test, then:
        service.run(PROJECT, SOURCE, 1000L, "parent-run-9");
        verify(runs).create(eq(PROJECT), eq("SYNTHETIC"), eq("MANUAL"), eq("local"),
                eq(List.of(SOURCE)), isNull(), eq("parent-run-9"));
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `./gradlew :domain:test --tests '*ReplayServiceTest' --tests '*SyntheticRunServiceTest'`
Expected: FAIL to compile (`replay`/`run` arity).

- [ ] **Step 3: Add the overloads**

`ReplayService.java` — rename the current body to take `parentRunId`, add a delegating 5-arg:
```java
    public ReplaySummary replay(String projectId, String dataSourceId, String recordingId,
            DeterministicSettings deterministicSettings, boolean compatibilityAck) {
        return replay(projectId, dataSourceId, recordingId, deterministicSettings, compatibilityAck, null);
    }

    public ReplaySummary replay(String projectId, String dataSourceId, String recordingId,
            DeterministicSettings deterministicSettings, boolean compatibilityAck, String parentRunId) {
        // ... existing body unchanged, EXCEPT the run creation line:
        RunRow run = runs.create(projectId, "REPLAY", "MANUAL", "local", List.of(dataSourceId), null, parentRunId);
        // ... rest unchanged
    }
```

`SyntheticRunService.java` — same shape:
```java
    public SyntheticRunSummary run(String projectId, String dataSourceId, long durationMs) {
        return run(projectId, dataSourceId, durationMs, null);
    }

    public SyntheticRunSummary run(String projectId, String dataSourceId, long durationMs, String parentRunId) {
        // ... existing body unchanged, EXCEPT the run creation line:
        RunRow run = runs.create(projectId, "SYNTHETIC", "MANUAL", "local", List.of(dataSourceId), null, parentRunId);
        // ... rest unchanged
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `./gradlew :domain:test --tests '*ReplayServiceTest' --tests '*SyntheticRunServiceTest'`
Expected: PASS (existing tests still green; new parent-linkage tests pass).

- [ ] **Step 5: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/replay/ReplayService.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunService.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/replay/ReplayServiceTest.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/synthetic/SyntheticRunServiceTest.java
git commit -m "feat(IS-086): parentRunId overloads on ReplayService/SyntheticRunService"
```

---

## Task 3: ScenarioValidationService

**Files:**
- Modify: `persistence/src/main/java/com/ainclusive/iotsim/persistence/scenario/ScenarioRepository.java`, `.../JooqScenarioRepository.java`
- Create: `domain/.../scenario/ValidationIssue.java`, `ScenarioValidation.java`, `ScenarioValidationService.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/scenario/ScenarioValidationServiceTest.java`

**Interfaces:**
- Consumes: `ScenarioRepository.findById(id) -> Optional<ScenarioRow>` (with `List<ScenarioStepRow> steps`), `DataSourceRepository.findById`, `RecordingRepository.findById`, `SchemaRepository.findCurrent(sourceId) -> Optional<SchemaWithNodes>` (has `.version()`), `ObjectMapper`.
- Produces:
  - `record ValidationIssue(int ordinal, String severity, String message)` (`severity` ∈ `ERROR|WARNING`; `ordinal = -1` for scenario-level).
  - `record ScenarioValidation(String status, List<ValidationIssue> issues)` (`status` ∈ `READY|INVALID`).
  - `ScenarioRepository.updateStatus(String id, String status) -> Optional<ScenarioRow>` (bumps version).
  - `ScenarioValidationService.validate(String projectId, String id) -> ScenarioValidation` (persists status).
  - `static final Set<String> EXECUTABLE_TYPES` and reuse `ScenarioService.STEP_TYPES` — but validation defines its own rules; make `ValidationIssue`/severity constants.

- [ ] **Step 1: Add `updateStatus` to the scenario repository**

`ScenarioRepository.java`, add:
```java
    /** Sets the derived validation status (READY|INVALID) and bumps version. */
    Optional<ScenarioRow> updateStatus(String id, String status);
```
`JooqScenarioRepository.java`, implement (mirror the `update` pattern; import `OffsetDateTime`/`ZoneOffset` already present):
```java
    @Override
    public Optional<ScenarioRow> updateStatus(String id, String status) {
        ScenariosRecord rec = dsl.update(SCENARIOS)
                .set(SCENARIOS.STATUS, status)
                .set(SCENARIOS.UPDATED_AT, OffsetDateTime.now(ZoneOffset.UTC))
                .set(SCENARIOS.VERSION, SCENARIOS.VERSION.plus(1))
                .where(SCENARIOS.ID.eq(id))
                .returning()
                .fetchOne();
        if (rec == null) {
            return Optional.empty();
        }
        return Optional.of(map(rec, fetchSteps(dsl, id)));
    }
```

- [ ] **Step 2: Write the domain records**

`ValidationIssue.java`:
```java
package com.ainclusive.iotsim.domain.scenario;

/** One validation finding. {@code ordinal} = -1 for scenario-level issues. */
public record ValidationIssue(int ordinal, String severity, String message) {
    public static final String ERROR = "ERROR";
    public static final String WARNING = "WARNING";
}
```
`ScenarioValidation.java`:
```java
package com.ainclusive.iotsim.domain.scenario;

import java.util.List;

/** Result of validating a scenario: derived status + the findings behind it. */
public record ScenarioValidation(String status, List<ValidationIssue> issues) {
    public static final String READY = "READY";
    public static final String INVALID = "INVALID";
}
```

- [ ] **Step 3: Write the failing service test**

`ScenarioValidationServiceTest.java` (in-memory fakes; mirror `ScenarioServiceTest` fakes for `ScenarioRepository`, and small fakes for `DataSourceRepository`/`RecordingRepository`/`SchemaRepository`). Cover: empty→INVALID; START missing target→INVALID; REPLAY missing recording→INVALID; REPLAY schema-mismatch→WARNING+READY; SYNTHETIC non-synthetic source→INVALID; SYNTHETIC durationMs≤0→INVALID; WAIT durationMs≤0→INVALID; MARKER→READY; FAULT→WARNING+READY; happy READY; status persisted (repo.updateStatus called with the derived status).
```java
package com.ainclusive.iotsim.domain.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
// ... imports for fakes + ObjectMapper + ScenarioRow/ScenarioStepRow + DataSourceRow/RecordingRow + SchemaWithNodes
import org.junit.jupiter.api.Test;

class ScenarioValidationServiceTest {

    // Build a service over in-memory fakes; helper makeScenario(String proj, ScenarioStepRow... steps)
    // seeds the fake ScenarioRepository and returns its id.

    @Test
    void emptyScenarioIsInvalid() {
        var f = new Fixture();
        String id = f.scenario();  // no steps
        ScenarioValidation v = f.service.validate(f.PROJECT, id);
        assertThat(v.status()).isEqualTo("INVALID");
        assertThat(v.issues()).anySatisfy(i -> assertThat(i.message()).contains("no steps"));
    }

    @Test
    void startStepWithMissingTargetIsInvalid() {
        var f = new Fixture();
        String id = f.scenario(new ScenarioStepRow(0, "START", "no-such-src", "{}"));
        assertThat(f.service.validate(f.PROJECT, id).status()).isEqualTo("INVALID");
    }

    @Test
    void replayWithoutRecordingIsInvalid() {
        var f = new Fixture();
        String id = f.scenario(new ScenarioStepRow(0, "REPLAY", f.SOURCE, "{}"));
        assertThat(f.service.validate(f.PROJECT, id).status()).isEqualTo("INVALID");
    }

    @Test
    void replaySchemaMismatchIsWarningButReady() {
        var f = new Fixture();  // recording schemaVersion=2, source current schema=1
        String id = f.scenario(new ScenarioStepRow(0, "REPLAY", f.SOURCE,
                "{\"recordingId\":\"" + f.RECORDING + "\"}"));
        ScenarioValidation v = f.service.validate(f.PROJECT, id);
        assertThat(v.status()).isEqualTo("READY");
        assertThat(v.issues()).anySatisfy(i -> assertThat(i.severity()).isEqualTo("WARNING"));
    }

    @Test
    void syntheticStepRequiresSyntheticSourceAndPositiveDuration() {
        var f = new Fixture();
        String id = f.scenario(new ScenarioStepRow(0, "SYNTHETIC", f.SOURCE, "{\"durationMs\":0}"));
        assertThat(f.service.validate(f.PROJECT, id).status()).isEqualTo("INVALID");
    }

    @Test
    void faultStepIsWarningButReady() {
        var f = new Fixture();
        String id = f.scenario(new ScenarioStepRow(0, "FAULT", null, "{}"));
        ScenarioValidation v = f.service.validate(f.PROJECT, id);
        assertThat(v.status()).isEqualTo("READY");
        assertThat(v.issues()).anySatisfy(i -> assertThat(i.severity()).isEqualTo("WARNING"));
    }

    @Test
    void markerAndValidStartAreReadyAndStatusPersisted() {
        var f = new Fixture();
        String id = f.scenario(new ScenarioStepRow(0, "START", f.SOURCE, "{}"),
                new ScenarioStepRow(1, "MARKER", null, "{\"label\":\"checkpoint\"}"));
        assertThat(f.service.validate(f.PROJECT, id).status()).isEqualTo("READY");
        assertThat(f.persistedStatus(id)).isEqualTo("READY");
    }
}
```
Implement `Fixture` with in-memory fakes: a `ScenarioRepository` that stores rows and records `updateStatus`; a `DataSourceRepository` returning a source for `f.SOURCE` (basis `SYNTHETIC`, with a valid `runtimeConfig` so the synthetic parse succeeds when `durationMs>0` — but the durationMs=0 test must fail on duration, not config); a `RecordingRepository` returning `f.RECORDING` at schemaVersion 2; a `SchemaRepository` returning current version 1. (Only implement the repository methods `ScenarioValidationService` actually calls; throw `UnsupportedOperationException` for the rest.)

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :domain:test --tests '*ScenarioValidationServiceTest'`
Expected: FAIL to compile — `ScenarioValidationService` not found.

- [ ] **Step 5: Write `ScenarioValidationService`**

```java
package com.ainclusive.iotsim.domain.scenario;

import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRepository;
import com.ainclusive.iotsim.persistence.datasource.DataSourceRow;
import com.ainclusive.iotsim.persistence.recording.RecordingRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepRow;
import com.ainclusive.iotsim.persistence.schema.SchemaRepository;
import com.ainclusive.iotsim.persistence.schema.SchemaWithNodes;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Computes and persists a scenario's READY/INVALID status from cross-entity checks (IS-086). */
@Service
public class ScenarioValidationService {

    private final ScenarioRepository scenarios;
    private final DataSourceRepository dataSources;
    private final RecordingRepository recordings;
    private final SchemaRepository schemas;
    private final ObjectMapper json;

    public ScenarioValidationService(ScenarioRepository scenarios, DataSourceRepository dataSources,
            RecordingRepository recordings, SchemaRepository schemas, ObjectMapper json) {
        this.scenarios = scenarios;
        this.dataSources = dataSources;
        this.recordings = recordings;
        this.schemas = schemas;
        this.json = json;
    }

    public ScenarioValidation validate(String projectId, String id) {
        ScenarioRow scenario = scenarios.findById(id)
                .filter(s -> s.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Scenario", id));

        List<ValidationIssue> issues = new ArrayList<>();
        if (scenario.steps().isEmpty()) {
            issues.add(new ValidationIssue(-1, ValidationIssue.ERROR, "scenario has no steps to run"));
        }
        for (ScenarioStepRow step : scenario.steps()) {
            validateStep(projectId, step, issues);
        }

        boolean hasError = issues.stream().anyMatch(i -> ValidationIssue.ERROR.equals(i.severity()));
        String status = hasError ? ScenarioValidation.INVALID : ScenarioValidation.READY;
        scenarios.updateStatus(id, status);
        return new ScenarioValidation(status, List.copyOf(issues));
    }

    private void validateStep(String projectId, ScenarioStepRow step, List<ValidationIssue> issues) {
        int at = step.ordinal();
        switch (step.type()) {
            case "START", "STOP" -> requireSource(projectId, step.targetSourceId(), at, issues);
            case "REPLAY" -> validateReplay(projectId, step, issues);
            case "SYNTHETIC" -> validateSynthetic(projectId, step, issues);
            case "WAIT" -> requirePositiveLong(step.params(), "durationMs", at, issues);
            case "MARKER" -> { /* always valid */ }
            case "FAULT" -> issues.add(new ValidationIssue(at, ValidationIssue.WARNING,
                    "FAULT steps are not executable until IS-087/IS-088"));
            default -> issues.add(new ValidationIssue(at, ValidationIssue.ERROR,
                    "unknown step type: " + step.type()));
        }
    }

    private Optional<DataSourceRow> requireSource(String projectId, String sourceId, int at,
            List<ValidationIssue> issues) {
        Optional<DataSourceRow> src = sourceId == null ? Optional.empty()
                : dataSources.findById(sourceId).filter(s -> s.projectId().equals(projectId));
        if (src.isEmpty()) {
            issues.add(new ValidationIssue(at, ValidationIssue.ERROR,
                    "targetSourceId does not exist in project: " + sourceId));
        }
        return src;
    }

    private void validateReplay(String projectId, ScenarioStepRow step, List<ValidationIssue> issues) {
        int at = step.ordinal();
        requireSource(projectId, step.targetSourceId(), at, issues);
        String recordingId = text(step.params(), "recordingId");
        if (recordingId == null || recordingId.isBlank()) {
            issues.add(new ValidationIssue(at, ValidationIssue.ERROR, "REPLAY step requires params.recordingId"));
            return;
        }
        var recording = recordings.findById(recordingId).filter(r -> r.projectId().equals(projectId));
        if (recording.isEmpty()) {
            issues.add(new ValidationIssue(at, ValidationIssue.ERROR,
                    "recording does not exist in project: " + recordingId));
            return;
        }
        int currentVersion = schemas.findCurrent(step.targetSourceId())
                .map(SchemaWithNodes::version).orElse(0);
        if (recording.get().schemaVersion() != currentVersion) {
            issues.add(new ValidationIssue(at, ValidationIssue.WARNING,
                    "recording schemaVersion " + recording.get().schemaVersion()
                            + " differs from source schema " + currentVersion
                            + " (needs compatibilityAck at run)"));
        }
    }

    private void validateSynthetic(String projectId, ScenarioStepRow step, List<ValidationIssue> issues) {
        int at = step.ordinal();
        Optional<DataSourceRow> src = requireSource(projectId, step.targetSourceId(), at, issues);
        src.ifPresent(s -> {
            if (!"SYNTHETIC".equals(s.basis())) {
                issues.add(new ValidationIssue(at, ValidationIssue.ERROR,
                        "SYNTHETIC step target is not a synthetic source: " + s.id()));
            }
        });
        requirePositiveLong(step.params(), "durationMs", at, issues);
    }

    private void requirePositiveLong(String paramsJson, String field, int at, List<ValidationIssue> issues) {
        Long v = longValue(paramsJson, field);
        if (v == null || v <= 0) {
            issues.add(new ValidationIssue(at, ValidationIssue.ERROR,
                    "step requires params." + field + " > 0"));
        }
    }

    private String text(String paramsJson, String field) {
        JsonNode n = node(paramsJson, field);
        return n != null && n.isString() ? n.asString() : null;
    }

    private Long longValue(String paramsJson, String field) {
        JsonNode n = node(paramsJson, field);
        return n != null && n.isNumber() ? n.asLong() : null;
    }

    private JsonNode node(String paramsJson, String field) {
        if (paramsJson == null || paramsJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = json.readTree(paramsJson);
            return root.isObject() ? root.get(field) : null;
        } catch (JacksonException e) {
            return null;
        }
    }
}
```
> Verify the Jackson 3 `JsonNode` accessors used here (`isString()`, `asString()`, `isNumber()`, `asLong()`) against the version in the repo (`grep -rn "JsonNode" domain/src/main` — `ScenarioService.normalizeJsonObject` already uses `isObject()`). If `asString()`/`isString()` differ (e.g. `asText()`/`isTextual()`), use the accessor that compiles.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :domain:test --tests '*ScenarioValidationServiceTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add persistence/src/main/java/com/ainclusive/iotsim/persistence/scenario \
        domain/src/main/java/com/ainclusive/iotsim/domain/scenario/ValidationIssue.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/scenario/ScenarioValidation.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/scenario/ScenarioValidationService.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/scenario/ScenarioValidationServiceTest.java
git commit -m "feat(IS-086): scenario validation service (READY/INVALID + persist status)"
```

---

## Task 4: ScenarioRunService

**Files:**
- Create: `domain/.../common/FeatureNotAvailableException.java`, `domain/.../common/ScenarioInvalidException.java`
- Create: `domain/.../scenario/StepOutcome.java`, `ScenarioRunSummary.java`, `ScenarioRunService.java`
- Test: `domain/src/test/java/com/ainclusive/iotsim/domain/scenario/ScenarioRunServiceTest.java`

**Interfaces:**
- Consumes: `ScenarioRepository.findById`; `ScenarioValidationService.validate` (Task 3); `RunRepository.create(...,parentRunId)`, `.start`, `.end` (Task 1); `EvidenceRepository.create(projectId, runId, createdBy)`, `.updateManifest(id, json)`; `RuntimeEventRepository.append(projectId, dataSourceId, runId, type, at, payloadJson)`; `DataSourceService.start(projectId, id)`/`.stop(projectId, id)`; `ReplayService.replay(..., parentRunId)` (Task 2); `SyntheticRunService.run(..., parentRunId)` (Task 2); `ObjectMapper`.
- Produces:
  - `record StepOutcome(int ordinal, String type, String childRunId, long applied, String state)`.
  - `record ScenarioRunSummary(String runId, String evidenceId, String status, List<StepOutcome> steps)`.
  - `ScenarioRunService.run(String projectId, String scenarioId, String trigger, String initiator) -> ScenarioRunSummary`.

- [ ] **Step 1: Write the exceptions**

`FeatureNotAvailableException.java`:
```java
package com.ainclusive.iotsim.domain.common;

/** A structurally valid request targets a capability not yet implemented (→ HTTP 501). */
public class FeatureNotAvailableException extends RuntimeException {
    public FeatureNotAvailableException(String message) {
        super(message);
    }
}
```
`ScenarioInvalidException.java`:
```java
package com.ainclusive.iotsim.domain.common;

import java.util.List;

/** A scenario failed validation and cannot be run (→ HTTP 422). Carries the issue messages. */
public class ScenarioInvalidException extends RuntimeException {
    private final transient List<String> issues;

    public ScenarioInvalidException(String scenarioId, List<String> issues) {
        super("scenario is INVALID: " + scenarioId);
        this.issues = List.copyOf(issues);
    }

    public List<String> issues() {
        return issues;
    }
}
```

- [ ] **Step 2: Write the summary records**

`StepOutcome.java`:
```java
package com.ainclusive.iotsim.domain.scenario;

/** Result of executing one scenario step. {@code childRunId}/{@code applied} are null/0 for non-run steps. */
public record StepOutcome(int ordinal, String type, String childRunId, long applied, String state) {}
```
`ScenarioRunSummary.java`:
```java
package com.ainclusive.iotsim.domain.scenario;

import java.util.List;

/** Outcome of a scenario run: the parent SCENARIO run + per-step results. */
public record ScenarioRunSummary(String runId, String evidenceId, String status, List<StepOutcome> steps) {}
```

- [ ] **Step 3: Write the failing service test (Mockito)**

`ScenarioRunServiceTest.java` — mock `ScenarioRepository`, `ScenarioValidationService`, `RunRepository`, `EvidenceRepository`, `RuntimeEventRepository`, `DataSourceService`, `ReplayService`, `SyntheticRunService`; real `ObjectMapper`. Cover: FAULT step → `FeatureNotAvailableException` (no run created); INVALID → `ScenarioInvalidException`; happy path START→REPLAY→STOP creates parent SCENARIO run, calls `replay(..., parentRunId=<parent>)`, ends COMPLETED, emits a step event per step; a mid-scenario failure → parent ended FAILED and later steps not executed; sourceIds union.
```java
package com.ainclusive.iotsim.domain.scenario;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ainclusive.iotsim.domain.common.FeatureNotAvailableException;
import com.ainclusive.iotsim.domain.common.ScenarioInvalidException;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.replay.ReplayService;
import com.ainclusive.iotsim.domain.replay.ReplaySummary;
import com.ainclusive.iotsim.domain.synthetic.SyntheticRunService;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepRow;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ScenarioRunServiceTest {

    private static final String PROJECT = "p1";
    private static final String SCENARIO = "scn-1";
    private static final String SOURCE = "ds-1";
    private static final String RECORDING = "rec-1";

    private ScenarioRepository scenarios;
    private ScenarioValidationService validation;
    private RunRepository runs;
    private EvidenceRepository evidence;
    private RuntimeEventRepository events;
    private DataSourceService dataSources;
    private ReplayService replay;
    private SyntheticRunService synthetic;
    private ScenarioRunService service;

    @BeforeEach
    void setUp() {
        scenarios = mock(ScenarioRepository.class);
        validation = mock(ScenarioValidationService.class);
        runs = mock(RunRepository.class);
        evidence = mock(EvidenceRepository.class);
        events = mock(RuntimeEventRepository.class);
        dataSources = mock(DataSourceService.class);
        replay = mock(ReplayService.class);
        synthetic = mock(SyntheticRunService.class);
        service = new ScenarioRunService(scenarios, validation, runs, evidence, events,
                dataSources, replay, synthetic, new ObjectMapper());
    }

    private void stubScenario(ScenarioStepRow... steps) {
        when(scenarios.findById(SCENARIO)).thenReturn(Optional.of(new ScenarioRow(
                SCENARIO, PROJECT, "Flow", "READY", "{}", List.of(steps),
                OffsetDateTime.now(), OffsetDateTime.now(), "local", 1)));
    }

    private void stubValidReady() {
        when(validation.validate(PROJECT, SCENARIO))
                .thenReturn(new ScenarioValidation("READY", List.of()));
    }

    private RunRow parentRun() {
        return new RunRow("run-parent", PROJECT, "SCENARIO", "MANUAL", "local", "RUNNING",
                SCENARIO, null, OffsetDateTime.now(), null, OffsetDateTime.now(), List.of(SOURCE), null);
    }

    @Test
    void faultStepIsRejectedWithoutCreatingARun() {
        stubScenario(new ScenarioStepRow(0, "FAULT", null, "{}"));
        assertThatThrownBy(() -> service.run(PROJECT, SCENARIO, "MANUAL", "local"))
                .isInstanceOf(FeatureNotAvailableException.class);
        verify(runs, never()).create(anyString(), anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void invalidScenarioIsRejected() {
        stubScenario(new ScenarioStepRow(0, "START", "missing", "{}"));
        when(validation.validate(PROJECT, SCENARIO))
                .thenReturn(new ScenarioValidation("INVALID",
                        List.of(new ValidationIssue(0, "ERROR", "targetSourceId does not exist"))));
        assertThatThrownBy(() -> service.run(PROJECT, SCENARIO, "MANUAL", "local"))
                .isInstanceOf(ScenarioInvalidException.class);
        verify(runs, never()).create(anyString(), anyString(), any(), any(), any(), any(), any());
    }

    @Test
    void happyPathRunsStepsUnderOneScenarioRunAndCompletes() {
        stubScenario(
                new ScenarioStepRow(0, "START", SOURCE, "{}"),
                new ScenarioStepRow(1, "REPLAY", SOURCE, "{\"recordingId\":\"" + RECORDING + "\"}"),
                new ScenarioStepRow(2, "STOP", SOURCE, "{}"));
        stubValidReady();
        RunRow parent = parentRun();
        when(runs.create(eq(PROJECT), eq("SCENARIO"), eq("MANUAL"), eq("local"), any(), eq(SCENARIO), eq((String) null)))
                .thenReturn(parent);
        when(runs.start(eq("run-parent"), any())).thenReturn(parent);
        when(runs.end(eq("run-parent"), anyString(), any())).thenReturn(parent);
        when(evidence.create(eq(PROJECT), eq("run-parent"), anyString()))
                .thenReturn(new EvidenceRow("ev-1", PROJECT, "run-parent", "CAPTURING", "{}", null, null, "local", 0));
        when(replay.replay(eq(PROJECT), eq(SOURCE), eq(RECORDING), any(), eq(false), eq("run-parent")))
                .thenReturn(new ReplaySummary(RECORDING, SOURCE, 42, "child-replay", "ev-2", null));

        ScenarioRunSummary summary = service.run(PROJECT, SCENARIO, "MANUAL", "local");

        assertThat(summary.runId()).isEqualTo("run-parent");
        assertThat(summary.status()).isEqualTo("COMPLETED");
        assertThat(summary.steps()).extracting(StepOutcome::type).containsExactly("START", "REPLAY", "STOP");
        verify(dataSources).start(PROJECT, SOURCE);
        verify(dataSources).stop(PROJECT, SOURCE);
        verify(replay).replay(eq(PROJECT), eq(SOURCE), eq(RECORDING), any(), eq(false), eq("run-parent"));
        verify(runs).end("run-parent", "COMPLETED", null == null ? any() : any()); // COMPLETED terminal
    }

    @Test
    void stepFailureEndsParentRunFailedAndSkipsRest() {
        stubScenario(
                new ScenarioStepRow(0, "START", SOURCE, "{}"),
                new ScenarioStepRow(1, "STOP", SOURCE, "{}"));
        stubValidReady();
        RunRow parent = parentRun();
        when(runs.create(eq(PROJECT), eq("SCENARIO"), any(), any(), any(), eq(SCENARIO), eq((String) null)))
                .thenReturn(parent);
        when(runs.start(anyString(), any())).thenReturn(parent);
        when(evidence.create(anyString(), anyString(), anyString()))
                .thenReturn(new EvidenceRow("ev-1", PROJECT, "run-parent", "CAPTURING", "{}", null, null, "local", 0));
        org.mockito.Mockito.doThrow(new RuntimeException("boom")).when(dataSources).start(PROJECT, SOURCE);

        assertThatThrownBy(() -> service.run(PROJECT, SCENARIO, "MANUAL", "local"))
                .isInstanceOf(RuntimeException.class);
        verify(runs).end(eq("run-parent"), eq("FAILED"), any());
        verify(dataSources, never()).stop(PROJECT, SOURCE);
    }
}
```
> Confirm the `EvidenceRow` constructor arity/field order against `persistence/.../evidence/EvidenceRow.java` and `ReplaySummary` against `domain/.../replay/ReplaySummary.java`; adjust the stub literals to match. The `verify(runs).end("run-parent", "COMPLETED", ...)` line: use `verify(runs).end(eq("run-parent"), eq("COMPLETED"), any());`.

- [ ] **Step 4: Run the test to verify it fails**

Run: `./gradlew :domain:test --tests '*ScenarioRunServiceTest'`
Expected: FAIL to compile — `ScenarioRunService` not found.

- [ ] **Step 5: Write `ScenarioRunService`**

```java
package com.ainclusive.iotsim.domain.scenario;

import com.ainclusive.iotsim.domain.common.FeatureNotAvailableException;
import com.ainclusive.iotsim.domain.common.ResourceNotFoundException;
import com.ainclusive.iotsim.domain.common.ScenarioInvalidException;
import com.ainclusive.iotsim.domain.datasource.DataSourceService;
import com.ainclusive.iotsim.domain.replay.ReplayService;
import com.ainclusive.iotsim.domain.replay.ReplaySummary;
import com.ainclusive.iotsim.domain.synthetic.SyntheticRunService;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRepository;
import com.ainclusive.iotsim.persistence.evidence.EvidenceRow;
import com.ainclusive.iotsim.persistence.run.RunRepository;
import com.ainclusive.iotsim.persistence.run.RunRow;
import com.ainclusive.iotsim.persistence.runtimeevent.RuntimeEventRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRepository;
import com.ainclusive.iotsim.persistence.scenario.ScenarioRow;
import com.ainclusive.iotsim.persistence.scenario.ScenarioStepRow;
import com.ainclusive.iotsim.protocolmodel.DeterministicSettings;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Executes a scenario's ordered steps as one SCENARIO run (IS-086). Synchronous, fail-fast. */
@Service
public class ScenarioRunService {

    private static final long MAX_WAIT_MS = 60_000L;

    private final ScenarioRepository scenarios;
    private final ScenarioValidationService validation;
    private final RunRepository runs;
    private final EvidenceRepository evidence;
    private final RuntimeEventRepository events;
    private final DataSourceService dataSources;
    private final ReplayService replay;
    private final SyntheticRunService synthetic;
    private final ObjectMapper json;

    public ScenarioRunService(ScenarioRepository scenarios, ScenarioValidationService validation,
            RunRepository runs, EvidenceRepository evidence, RuntimeEventRepository events,
            DataSourceService dataSources, ReplayService replay, SyntheticRunService synthetic,
            ObjectMapper json) {
        this.scenarios = scenarios;
        this.validation = validation;
        this.runs = runs;
        this.evidence = evidence;
        this.events = events;
        this.dataSources = dataSources;
        this.replay = replay;
        this.synthetic = synthetic;
        this.json = json;
    }

    public ScenarioRunSummary run(String projectId, String scenarioId, String trigger, String initiator) {
        ScenarioRow scenario = scenarios.findById(scenarioId)
                .filter(s -> s.projectId().equals(projectId))
                .orElseThrow(() -> new ResourceNotFoundException("Scenario", scenarioId));

        // Pre-flight, before any run row exists.
        if (scenario.steps().stream().anyMatch(s -> "FAULT".equals(s.type()))) {
            throw new FeatureNotAvailableException(
                    "scenario contains a FAULT step; fault injection is not available until IS-087/IS-088");
        }
        ScenarioValidation v = validation.validate(projectId, scenarioId);
        if (ScenarioValidation.INVALID.equals(v.status())) {
            throw new ScenarioInvalidException(scenarioId,
                    v.issues().stream().map(ValidationIssue::message).toList());
        }

        String trig = trigger != null ? trigger : "MANUAL";
        String actor = initiator != null && !initiator.isBlank() ? initiator : "local";
        List<String> sourceIds = List.copyOf(new LinkedHashSet<>(scenario.steps().stream()
                .map(ScenarioStepRow::targetSourceId).filter(s -> s != null && !s.isBlank()).toList()));

        RunRow run = runs.create(projectId, "SCENARIO", trig, actor, sourceIds, scenarioId, null);
        DeterministicSettings settings = parseSettings(scenario.deterministicSettings());
        List<StepOutcome> outcomes = new ArrayList<>();
        try {
            runs.start(run.id(), now());
            EvidenceRow ev = evidence.create(projectId, run.id(), actor);
            runs.linkEvidence(run.id(), ev.id());
            evidence.updateManifest(ev.id(), manifest(scenario));

            for (ScenarioStepRow step : scenario.steps()) {
                events.append(projectId, step.targetSourceId(), run.id(), "SCENARIO_STEP", now(),
                        stepPayload(step));
                outcomes.add(execute(projectId, step, run.id(), settings));
            }
            RunRow ended = runs.end(run.id(), "COMPLETED", now());
            return new ScenarioRunSummary(run.id(), ev.id(), ended.state(), outcomes);
        } catch (RuntimeException e) {
            runs.end(run.id(), "FAILED", now());
            throw e;
        }
    }

    private StepOutcome execute(String projectId, ScenarioStepRow step, String parentRunId,
            DeterministicSettings settings) {
        int at = step.ordinal();
        switch (step.type()) {
            case "START" -> {
                dataSources.start(projectId, step.targetSourceId());
                return new StepOutcome(at, "START", null, 0, "OK");
            }
            case "STOP" -> {
                dataSources.stop(projectId, step.targetSourceId());
                return new StepOutcome(at, "STOP", null, 0, "OK");
            }
            case "REPLAY" -> {
                ReplaySummary s = replay.replay(projectId, step.targetSourceId(),
                        text(step.params(), "recordingId"), settings,
                        bool(step.params(), "compatibilityAck"), parentRunId);
                return new StepOutcome(at, "REPLAY", s.runId(), s.applied(), "OK");
            }
            case "SYNTHETIC" -> {
                var s = synthetic.run(projectId, step.targetSourceId(),
                        longValue(step.params(), "durationMs"), parentRunId);
                return new StepOutcome(at, "SYNTHETIC", s.runId(), s.applied(), "OK");
            }
            case "WAIT" -> {
                sleep(Math.min(longValue(step.params(), "durationMs"), MAX_WAIT_MS));
                return new StepOutcome(at, "WAIT", null, 0, "OK");
            }
            case "MARKER" -> {
                return new StepOutcome(at, "MARKER", null, 0, "OK");
            }
            default -> throw new IllegalArgumentException("unexecutable step type: " + step.type());
        }
    }

    private DeterministicSettings parseSettings(String detJson) {
        Long seed = longValue(detJson, "seed");
        if (seed == null) {
            return null;
        }
        String startTime = text(detJson, "startTime");
        Instant start = startTime != null ? Instant.parse(startTime) : Instant.now();
        return new DeterministicSettings(seed, start);
    }

    private String manifest(ScenarioRow scenario) {
        return json.writeValueAsString(Map.of(
                "scenario", true,
                "scenarioId", scenario.id(),
                "name", scenario.name(),
                "stepCount", scenario.steps().size()));
    }

    private String stepPayload(ScenarioStepRow step) {
        String label = text(step.params(), "label");
        return json.writeValueAsString(Map.of(
                "ordinal", step.ordinal(),
                "type", step.type(),
                "label", label != null ? label : ""));
    }

    // ---- small JSON param readers (tolerant: return null/false on absence/parse failure) ----

    private String text(String jsonStr, String field) {
        JsonNode n = node(jsonStr, field);
        return n != null && n.isString() ? n.asString() : null;
    }

    private long longValue(String jsonStr, String field) {
        JsonNode n = node(jsonStr, field);
        return n != null && n.isNumber() ? n.asLong() : 0L;
    }

    private boolean bool(String jsonStr, String field) {
        JsonNode n = node(jsonStr, field);
        return n != null && n.isBoolean() && n.asBoolean();
    }

    private JsonNode node(String jsonStr, String field) {
        if (jsonStr == null || jsonStr.isBlank()) {
            return null;
        }
        try {
            JsonNode root = json.readTree(jsonStr);
            return root.isObject() ? root.get(field) : null;
        } catch (JacksonException e) {
            return null;
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("scenario WAIT interrupted", e);
        }
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }
}
```
> Same Jackson accessor caveat as Task 3 (`isString/asString/isNumber/asLong/isBoolean/asBoolean`) — verify and adjust to whatever compiles in this repo's Jackson 3.

- [ ] **Step 6: Run the test to verify it passes**

Run: `./gradlew :domain:test --tests '*ScenarioRunServiceTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add domain/src/main/java/com/ainclusive/iotsim/domain/common/FeatureNotAvailableException.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/common/ScenarioInvalidException.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/scenario/StepOutcome.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/scenario/ScenarioRunSummary.java \
        domain/src/main/java/com/ainclusive/iotsim/domain/scenario/ScenarioRunService.java \
        domain/src/test/java/com/ainclusive/iotsim/domain/scenario/ScenarioRunServiceTest.java
git commit -m "feat(IS-086): scenario run execution service (sequential, fail-fast, child runs)"
```

---

## Task 5: API — `/validate` + `/run` + error mappings

**Files:**
- Modify: `api/src/main/java/com/ainclusive/iotsim/api/error/GlobalExceptionHandler.java`
- Modify: `api/src/main/java/com/ainclusive/iotsim/api/scenario/ScenarioController.java`
- Test: `api/src/test/java/com/ainclusive/iotsim/api/scenario/ScenarioControllerRunTest.java`

**Interfaces:**
- Consumes: `ScenarioValidationService.validate` (Task 3), `ScenarioRunService.run` (Task 4), `FeatureNotAvailableException`, `ScenarioInvalidException` (Task 4), `ScenarioValidation`/`ValidationIssue`/`ScenarioRunSummary`/`StepOutcome`.
- Produces: `GET /api/v1/projects/{projectId}/scenarios/{id}/validate` and `POST /api/v1/projects/{projectId}/scenarios/{id}/run`.

- [ ] **Step 1: Map the new exceptions in `GlobalExceptionHandler`**

Add imports for `com.ainclusive.iotsim.domain.common.FeatureNotAvailableException` and `...ScenarioInvalidException`, and two handlers:
```java
    @ExceptionHandler(FeatureNotAvailableException.class)
    public ProblemDetail featureNotAvailable(FeatureNotAvailableException e) {
        return problem(HttpStatus.NOT_IMPLEMENTED, e.getMessage());
    }

    @ExceptionHandler(ScenarioInvalidException.class)
    public ProblemDetail scenarioInvalid(ScenarioInvalidException e) {
        ProblemDetail pd = problem(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        pd.setProperty("issues", e.issues());
        return pd;
    }
```

- [ ] **Step 2: Add the endpoints + DTOs to `ScenarioController`**

Inject the two services (add constructor params — the controller currently takes only `ScenarioService`):
```java
    private final ScenarioService scenarios;
    private final ScenarioValidationService validationService;
    private final ScenarioRunService runService;

    public ScenarioController(ScenarioService scenarios,
            ScenarioValidationService validationService, ScenarioRunService runService) {
        this.scenarios = scenarios;
        this.validationService = validationService;
        this.runService = runService;
    }
```
Add the endpoints:
```java
    @GetMapping("/{id}/validate")
    public ScenarioValidationResponse validate(@PathVariable String projectId, @PathVariable String id) {
        return ScenarioValidationResponse.from(validationService.validate(projectId, id));
    }

    @PostMapping("/{id}/run")
    public ScenarioRunResponse run(@PathVariable String projectId, @PathVariable String id,
            @RequestBody(required = false) RunScenarioRequest req) {
        String trigger = req != null ? req.trigger() : null;
        String initiator = req != null ? req.initiator() : null;
        return ScenarioRunResponse.from(runService.run(projectId, id, trigger, initiator));
    }
```
Add the DTO records (nested, mirroring the existing `StepDto`/`ScenarioResponse` style):
```java
    public record RunScenarioRequest(String trigger, String initiator) {}

    public record ValidationIssueResponse(int ordinal, String severity, String message) {
        static ValidationIssueResponse from(ValidationIssue i) {
            return new ValidationIssueResponse(i.ordinal(), i.severity(), i.message());
        }
    }

    public record ScenarioValidationResponse(String status, List<ValidationIssueResponse> issues) {
        static ScenarioValidationResponse from(ScenarioValidation v) {
            return new ScenarioValidationResponse(v.status(),
                    v.issues().stream().map(ValidationIssueResponse::from).toList());
        }
    }

    public record StepOutcomeResponse(int ordinal, String type, String childRunId, long applied, String state) {
        static StepOutcomeResponse from(StepOutcome o) {
            return new StepOutcomeResponse(o.ordinal(), o.type(), o.childRunId(), o.applied(), o.state());
        }
    }

    public record ScenarioRunResponse(String runId, String evidenceId, String status,
            List<StepOutcomeResponse> steps) {
        static ScenarioRunResponse from(ScenarioRunSummary s) {
            return new ScenarioRunResponse(s.runId(), s.evidenceId(), s.status(),
                    s.steps().stream().map(StepOutcomeResponse::from).toList());
        }
    }
```
Add imports: `ScenarioValidationService`, `ScenarioRunService`, `ScenarioValidation`, `ValidationIssue`, `ScenarioRunSummary`, `StepOutcome`, `org.springframework.web.bind.annotation.RequestBody` (already imported).

**Also update the existing IS-085 test:** `api/src/test/java/com/ainclusive/iotsim/api/scenario/ScenarioControllerTest.java` constructs `new ScenarioController(svc)` (1-arg) in several tests. The ctor is now 3-arg — change every `new ScenarioController(<x>)` there to `new ScenarioController(<x>, null, null)` (those tests exercise only the CRUD endpoints, which don't touch the validation/run services). Run `./gradlew :api:test --tests '*ScenarioControllerTest'` to confirm it still passes after the change.

- [ ] **Step 3: Write the controller unit test (POJO, capturing fakes)**

`ScenarioControllerRunTest.java` — subclass `ScenarioValidationService`/`ScenarioRunService` (both `@Service` concrete, non-final) with `super(...nulls...)` and override `validate`/`run`; instantiate `ScenarioController(new ScenarioService-fake-or-null, fakeValidation, fakeRun)` (pass `null` for `ScenarioService` since these endpoints don't use it), and assert the mapped responses. (Match the existing `ScenarioControllerTest` POJO style.)
```java
package com.ainclusive.iotsim.api.scenario;

import static org.assertj.core.api.Assertions.assertThat;

import com.ainclusive.iotsim.api.scenario.ScenarioController.ScenarioRunResponse;
import com.ainclusive.iotsim.api.scenario.ScenarioController.ScenarioValidationResponse;
import com.ainclusive.iotsim.domain.scenario.ScenarioRunService;
import com.ainclusive.iotsim.domain.scenario.ScenarioRunSummary;
import com.ainclusive.iotsim.domain.scenario.ScenarioValidation;
import com.ainclusive.iotsim.domain.scenario.ScenarioValidationService;
import com.ainclusive.iotsim.domain.scenario.StepOutcome;
import com.ainclusive.iotsim.domain.scenario.ValidationIssue;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioControllerRunTest {

    private static final class FakeValidation extends ScenarioValidationService {
        FakeValidation() { super(null, null, null, null, null); }
        @Override public ScenarioValidation validate(String p, String id) {
            return new ScenarioValidation("INVALID", List.of(new ValidationIssue(0, "ERROR", "bad")));
        }
    }

    private static final class FakeRun extends ScenarioRunService {
        FakeRun() { super(null, null, null, null, null, null, null, null, null); }
        @Override public ScenarioRunSummary run(String p, String id, String trigger, String initiator) {
            return new ScenarioRunSummary("run-1", "ev-1", "COMPLETED",
                    List.of(new StepOutcome(0, "MARKER", null, 0, "OK")));
        }
    }

    @Test
    void validateMapsStatusAndIssues() {
        ScenarioController c = new ScenarioController(null, new FakeValidation(), new FakeRun());
        ScenarioValidationResponse resp = c.validate("p1", "scn-1");
        assertThat(resp.status()).isEqualTo("INVALID");
        assertThat(resp.issues()).singleElement()
                .satisfies(i -> assertThat(i.message()).isEqualTo("bad"));
    }

    @Test
    void runMapsSummary() {
        ScenarioController c = new ScenarioController(null, new FakeValidation(), new FakeRun());
        ScenarioRunResponse resp = c.run("p1", "scn-1", null);
        assertThat(resp.runId()).isEqualTo("run-1");
        assertThat(resp.status()).isEqualTo("COMPLETED");
        assertThat(resp.steps()).singleElement().satisfies(s -> assertThat(s.type()).isEqualTo("MARKER"));
    }
}
```
> The 501 (FAULT) and 422 (INVALID) HTTP status mapping goes through `GlobalExceptionHandler`, which a POJO test does not exercise — that HTTP-layer assertion belongs to the IS-121 MockMvc harness. This POJO test covers the controller's response mapping only.

- [ ] **Step 4: Run the test**

Run: `./gradlew :api:test --tests '*ScenarioControllerRunTest'`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add api/src/main/java/com/ainclusive/iotsim/api/error/GlobalExceptionHandler.java \
        api/src/main/java/com/ainclusive/iotsim/api/scenario/ScenarioController.java \
        api/src/test/java/com/ainclusive/iotsim/api/scenario/ScenarioControllerRunTest.java \
        api/src/test/java/com/ainclusive/iotsim/api/scenario/ScenarioControllerTest.java
git commit -m "feat(IS-086): scenario /validate + /run endpoints + 501/422 mappings"
```

---

## Task 6: Full verification

- [ ] **Step 1: Full build** ([[always-compile-and-test]])

Run (Colima env so ITs run, and `--rerun-tasks` so jOOQ regenerates against V8 and nothing is cache-masked):
```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
./gradlew build --rerun-tasks
```
Expected: BUILD SUCCESSFUL. `RunRepositoryIT` (incl. `childRunCarriesParentRunId`), `ScenarioValidationServiceTest`, `ScenarioRunServiceTest`, `ScenarioControllerRunTest`, and the existing replay/synthetic/evidence tests all pass; the app context-boot smoke wires the new `ScenarioValidationService`/`ScenarioRunService` beans.

- [ ] **Step 2: Confirm ITs executed (not skipped)**

Verify `RunRepositoryIT` shows as executed in the report. If skipped, re-export the Colima Docker env vars and re-run.

- [ ] **Step 3: Open the PR**

Hand off to `/open-pr` — it flips the `IS-086` checkbox in `backend-specs/TASKS.md` in the same PR, creates the PR with `Implements: IS-086`, arms squash auto-merge, moves the board to In review.

---

## Self-Review

**Spec coverage:**
- `GET /validate` computes + persists READY/INVALID → Task 3 (service) + Task 5 (endpoint). ✅
- `POST /run` executes ordered steps as a SCENARIO run → Task 4 + Task 5. ✅
- Nested child runs + `parent_run_id` → Task 1 (migration + repo) + Task 2 (overloads) + Task 4 (passes parentRunId). ✅
- FAULT pre-flight 501 → Task 4 (`FeatureNotAvailableException`) + Task 5 (mapping). ✅
- INVALID → 422 → Task 4 (`ScenarioInvalidException`) + Task 5 (mapping). ✅
- Validation rules (empty, targets exist, recording exists, schema-mismatch WARNING, synthetic basis + durationMs, WAIT durationMs, MARKER, FAULT WARNING) → Task 3. ✅
- START/STOP/REPLAY/SYNTHETIC/WAIT/MARKER execution + fail-fast + WAIT cap + step events → Task 4. ✅
- Determinism best-effort parse for REPLAY → Task 4 (`parseSettings`). ✅
- Out-of-scope (FAULT exec, live-SSE, async, scenario stop, UI) → not implemented. ✅

**Placeholder scan:** No TBD/TODO. The Jackson-accessor verify notes (Tasks 3–4) and the `EvidenceRow`/`ReplaySummary` arity-confirm note (Task 4) are explicit verify-or-adjust instructions, not placeholders.

**Type consistency:** `RunRow(...,parentRunId)` trailing field used consistently (Task 1) across `JooqRunRepository`, the 3 test sites, and the `RunRepository.create` 7-arg. `ScenarioValidation(status, issues)` / `ValidationIssue(ordinal, severity, message)` / `ScenarioRunSummary(runId, evidenceId, status, steps)` / `StepOutcome(ordinal, type, childRunId, applied, state)` are used identically in Tasks 3, 4, 5. `ReplayService.replay(...,parentRunId)` and `SyntheticRunService.run(...,parentRunId)` (Task 2) match their call sites in `ScenarioRunService` (Task 4). Controller ctor gains `ScenarioValidationService`/`ScenarioRunService` (Task 5) — the IS-085 `ScenarioControllerTest` constructs `new ScenarioController(svc)`; **Task 5 must update that existing test's constructor calls** to the 3-arg form (pass the fake/null validation+run services) or it will not compile. Note added to Task 5.
```
