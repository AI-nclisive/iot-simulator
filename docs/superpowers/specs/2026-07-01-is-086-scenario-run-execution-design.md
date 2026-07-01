# IS-086 — Scenario validation + run execution (design)

**Task:** IS-086 [BE] · Wave F — Advanced workflows & hardening · P2
**Issue:** [#107](https://github.com/AI-nclisive/iot-simulator/issues/107)
**Owning spec:** `backend-specs/05_API_CONTRACT.md` (`GET /scenarios/{id}/validate`, `POST /scenarios/{id}/run`) + `03_DOMAIN_MODEL.md` (Scenario/Run) + `06_ARTIFACT_FORMATS.md` (step params); supports `SPEC.md` → *Run Deterministic Scenarios*
**Branch:** `feat/IS-086-scenario-run-execution`
**Builds on:** IS-085 (scenario model + CRUD, merged). Prerequisite for IS-089 (unified `/runs` incl. scenario runs).

## Problem

IS-085 delivered scenario authoring (model + steps + CRUD), with `status` always
`DRAFT` and no execution. IS-086 makes scenarios runnable:

- `GET /scenarios/{id}/validate` — compute and persist `READY`/`INVALID` from
  cross-entity checks (targets exist, required params present).
- `POST /scenarios/{id}/run` — execute the ordered steps, opening a `SCENARIO`
  run that orchestrates the existing replay/synthetic/runtime services.

## Decisions (agreed in brainstorming)

1. **Run model — nested child runs + `parent_run_id`.** A scenario run is a
   `SCENARIO` parent run; `REPLAY`/`SYNTHETIC` steps open their own child runs
   (reusing `ReplayService`/`SyntheticRunService`), linked to the parent via a
   new `runs.parent_run_id` FK. Preserves per-step granularity (independent run
   rows, per-step evidence, `runId`-scoped runtime events, per-step
   reproducibility) while keeping the hierarchy explicit. IS-089's `/runs` will
   render the hierarchy.
2. **FAULT — pre-flight reject (501).** Validation treats `FAULT` as
   structurally valid (known type) and emits a `WARNING` "not executable until
   IS-087/088"; it does **not** mark the scenario `INVALID`. `POST /run` rejects
   a scenario containing any `FAULT` step **before** creating a run
   (`501 NOT_IMPLEMENTED`), so no half-run is left behind.
3. **`GET /validate` persists `status`.** It returns a report (status + issues)
   **and** writes the computed `READY`/`INVALID` to the scenario row (bumping
   `version`) so list/detail/UI reflect validity. (GET-with-side-effect is
   accepted because `status` is a persisted part of the domain model.)

## Run/execution model

Synchronous, sequential — mirrors `ReplayService`/`SyntheticRunService` (bounded,
one-shot; `POST /run` blocks until done). **Fail-fast:** an exception on any step
ends the parent run `FAILED` (any in-flight child run ends `FAILED` via its own
try/catch) and remaining steps do not execute.

## Components (domain)

```
domain/.../scenario/
  ValidationIssue.java           record(int ordinal /* -1 = scenario-level */, String severity /* ERROR|WARNING */, String message)
  ScenarioValidation.java        record(String status /* READY|INVALID */, List<ValidationIssue> issues)
  ScenarioValidationService.java validate(projectId, id) → ScenarioValidation; persists status (bump version)
  StepOutcome.java               record(int ordinal, String type, String childRunId /* nullable */, long applied, String state)
  ScenarioRunSummary.java        record(String runId, String evidenceId, String status, List<StepOutcome> steps)
  ScenarioRunService.java        run(projectId, id, trigger, initiator) → ScenarioRunSummary
```

`ScenarioValidationService` and `ScenarioRunService` are separate services —
`ScenarioService` keeps its CRUD responsibility.

## Persistence change

- **Flyway migration V8** (next collision-safe version via `/flyway-migration`):
  `alter table runs add column parent_run_id varchar references runs(id) on delete cascade;`
  plus an index on `parent_run_id`. Regenerate jOOQ.
- `RunRepository`: add an overload `create(projectId, kind, trigger, initiator,
  sourceIds, scenarioId, parentRunId)`; the existing 6-arg `create` delegates
  with `parentRunId = null` (keeps existing callers/tests untouched). `RunRow`
  gains `parentRunId`; `JooqRunRepository` sets/reads the column.
- `ReplayService.replay(...)` and `SyntheticRunService.run(...)` gain an overload
  taking `parentRunId`; the existing public signatures delegate with `null`
  (IS-065 / replay tests unaffected).

## Validation rules (`GET /scenarios/{id}/validate`)

Computes issues per step, derives status, persists it. `status = INVALID` if any
`ERROR` issue, else `READY`.

- Scenario with **0 steps** → `INVALID` ("no steps to run").
- **START / STOP:** `targetSourceId` must exist in the project (ERROR if not).
- **REPLAY:** `targetSourceId` exists (ERROR); `params.recordingId` present and
  the recording exists in the project (ERROR). Recording `schemaVersion` ≠ the
  source's current schema version → `WARNING` (not INVALID — `compatibilityAck`
  resolves it at run time).
- **SYNTHETIC:** `targetSourceId` exists, `basis = SYNTHETIC`, `runtimeConfig`
  parses (ERROR otherwise); `params.durationMs` present and > 0 (ERROR).
- **WAIT:** `params.durationMs` present and > 0 (ERROR).
- **MARKER:** always valid (label optional).
- **FAULT:** structurally valid → `WARNING` "not executable until IS-087/088";
  does not make the scenario INVALID.

Note: a `READY` scenario containing a `FAULT` step is still rejected by `/run`
(501) — READY means structurally valid, not necessarily runnable in this release.

## Execution (`POST /scenarios/{id}/run`)

Optional body `{ trigger?, initiator? }` (`trigger` defaults `MANUAL`,
`initiator` defaults `"local"`; automation initiator is never anonymous).

**Pre-flight (before any run row):**
- any `FAULT` step → `501` "fault injection not available until IS-087/088".
- validation returns `INVALID` → `422` with the issues.

**Then:**
1. Create the parent `SCENARIO` run (`kind=SCENARIO`, `scenarioId`, `sourceIds` =
   union of step targets, `parentRunId = null`), `start` it, create a `CAPTURING`
   evidence row and link it; manifest seeded with scenario name + step list.
2. For each step (ordinal `0..n-1`): append a `SCENARIO_STEP` runtime event
   (`RuntimeEventRepository.append`, payload `{ordinal, type, label?}`, scoped to
   the parent run id), then execute:
   - **START / STOP** → `DataSourceService.start/stop` (inline; no child run).
   - **REPLAY** → `ReplayService.replay(..., parentRunId = scenarioRunId)` → child
     `REPLAY` run + evidence. Determinism: the scenario's `deterministicSettings`
     JSON is parsed **best-effort** for `{seed, startTime}` — if it yields a valid
     seed, that `DeterministicSettings` is passed to each replay step; if the JSON
     is empty/absent/lacks a seed, `null` is passed and `ReplayService` falls back
     to its own random-with-capture (existing behavior). `compatibilityAck` from
     `params`.
   - **SYNTHETIC** → `SyntheticRunService.run(..., parentRunId = scenarioRunId)` →
     child `SYNTHETIC` run + evidence. `durationMs` from `params`.
   - **WAIT** → bounded real sleep of `params.durationMs`, capped at `60_000` ms.
     Determinism caveat: wall-clock, outside the seed-based value generation.
   - **MARKER** → runtime event only (label from `params.label`).
3. Fail-fast: on exception → parent run `FAILED`, re-throw; all steps succeed →
   parent run `COMPLETED`.
4. Return `ScenarioRunSummary` (parent runId, evidenceId, status, per-step
   outcomes with childRunId where applicable).

## API (`ScenarioController`, extends IS-085)

| Method | Path | Behavior |
|---|---|---|
| `GET` | `/scenarios/{id}/validate` | → `ScenarioValidationResponse` (status + issues); persists status |
| `POST` | `/scenarios/{id}/run` | opt. body `{trigger?, initiator?}` → 200 + `ScenarioRunResponse`; `501` (FAULT), `422` (INVALID), `404` |

## Error handling

Reuses the `error` package: `ResourceNotFoundException` (404),
`IllegalArgumentException` (400, malformed params). New
`ScenarioInvalidException` (carries the issues) → **422**. FAULT pre-flight →
**501** (add a `GlobalExceptionHandler` mapping — a dedicated
`FeatureNotAvailableException`, or reuse the existing UNSUPPORTED→501 path).

## Runtime events

Scenario-step changes are recorded in runtime-event **history** via
`RuntimeEventRepository.append` (satisfies `03`/SPEC "scenario step changes").
Live SSE fan-out of these events is **out of scope** (history only) — the live
stream is fed from the IPC listener, not domain appends.

## Testing (TDD)

- **Persistence IT:** `parent_run_id` round-trips; a child run links to its parent.
- **ScenarioValidationService unit:** each rule (missing target/recording, bad
  params, empty scenario, FAULT→WARNING/READY, schema-mismatch→WARNING);
  status persisted + version bumped.
- **ScenarioRunService unit** (in-memory fakes + fake replay/synthetic/data-source
  delegates): step order, child runs carry `parentRunId`, fail-fast → parent
  FAILED + remaining steps skipped, step events emitted, WAIT cap, MARKER,
  COMPLETED happy path, sourceIds union.
- **Replay/Synthetic:** a test that the `parentRunId` overload sets it on the
  child run (existing null-parent behavior unchanged).
- **ScenarioControllerTest (POJO):** `/validate` statuses; `/run` 200 /
  501 (FAULT) / 422 (INVALID); outcome mapping.

## Out of scope

FAULT execution (IS-087/088), live-SSE fan-out of step events, async/long-running
scenarios, `POST /runs/{runId}/stop` for scenarios (IS-089), UI.

## Definition of done

`./gradlew build` green (incl. new migration + regenerated jOOQ + ITs); IS-086
checkbox flipped in `backend-specs/TASKS.md` in the same PR; board → In review
via `/open-pr`.
