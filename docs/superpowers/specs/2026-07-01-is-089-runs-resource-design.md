# IS-089 — Runs resource + test-control endpoints (design)

**Task:** IS-089 [BE] · Wave F — Advanced workflows & hardening · P2
**Issue:** [#110](https://github.com/AI-nclisive/iot-simulator/issues/110)
**Owning spec:** `backend-specs/05_API_CONTRACT.md` §"Runs (and test-control for automation)"; `SPEC.md` → *Control Simulations From Automated Tests* (P1)
**Branch:** `feat/IS-089-runs-resource`
**Builds on:** RunRepository (+`parentRunId`, IS-124), the three run services (ReplayService/SyntheticRunService/ScenarioRunService, IS-028/065/086), IS-122 `ActiveRunService` enrichment, `RuntimeController.health`.

## Problem

There is no unified `/runs` REST resource. Runs are created only through
per-resource endpoints (`…/data-sources/{id}/replay`, `…/run-synthetic`,
`…/scenarios/{id}/run`), and the only run listing is IS-122's `…/active-runs`
(active only, read-only). Automated regression flows (`SPEC.md` "Control
Simulations From Automated Tests", P1) need a single test-control surface: start a
run, poll its readiness/state, stop it, and list/get runs — with a non-anonymous
automation initiator.

## Decisions (agreed in brainstorming)

1. **`POST /runs` starts all three kinds** (`REPLAY`/`SYNTHETIC`/`SCENARIO`) via a
   discriminated body, routing to the existing services, with **`trigger=AUTOMATION`
   and a required non-anonymous `initiator`** threaded through. This requires
   `ReplayService`/`SyntheticRunService` to accept `trigger`/`initiator` (they
   currently hardcode `MANUAL`/`local`); `ScenarioRunService.run` already does.
2. **`POST /runs/{id}/stop`** stops the run's participating sources
   (`runtime.stop` each → tears down workers/endpoints) and transitions the run to
   `STOPPED` if it is not already terminal. A terminal run's sources are still
   stopped, but its state is left as-is.
3. Leave IS-122's `/active-runs` as-is (a filtered dashboard view); `/runs` is the
   general resource. `parentRunId` (IS-124) is exposed as a field (no separate
   nested children view).

## Components

### 1. Persistence — `RunRepository.findByProjectPaged`
Add `findByProjectPaged(String projectId, OffsetDateTime afterAt, String afterId,
int limit) -> List<RunRow>` (+ `JooqRunRepository` impl), sort `created_at DESC,
id DESC` — cursor pagination consistent with IS-074. (`findById`, `findByProject`,
`findActiveByProject`, `create(…,parentRunId)`, `start`, `end`, `linkEvidence`
already exist.)

### 2. Domain — `RunService` (`domain/.../run/`)
- `record RunView(String id, String projectId, String kind, String trigger,
  String initiator, String state, String scenarioId, String evidenceId,
  String parentRunId, List<String> sourceIds, Instant startedAt, Instant endedAt,
  Instant createdAt, String label, String relatedLabel)`.
- `Page<RunView> listPaged(projectId, cursor, Integer limit)` — all runs, enriched
  (label/`relatedLabel` derived like `ActiveRunService`: batch-load data sources +
  scenarios, no N+1). Extract the enrichment into a small shared helper reused by
  both `RunService` and `ActiveRunService`, or mirror it — decided in the plan.
- `RunView get(projectId, id)` — 404 (`ResourceNotFoundException`) if missing or
  wrong project.
- `RunState stateOf(projectId, id)` — `record RunState(String runState,
  List<SourceState> sources)`, `record SourceState(String sourceId, String state,
  String lastError)`; per source, `RuntimeController.health(sourceId)`.
- `RunView stop(projectId, id)` — require the run; `runtime.stop(sourceId)` for each
  participating source; if the run's state is non-terminal (`QUEUED`/`RUNNING`),
  `runs.end(id, "STOPPED", now())`; return the (re-read) view.
- `RunView start(projectId, StartRunCommand cmd)` — validate + route by `kind` to
  the right service with `trigger="AUTOMATION"` + `cmd.initiator()`; re-read the
  created run (`runs.findById(summary.runId())`) → `RunView`.
  - `REPLAY` → `replay(projectId, dataSourceId, recordingId, settings, compatibilityAck, "AUTOMATION", initiator, null)`
  - `SYNTHETIC` → `run(projectId, dataSourceId, durationMs, "AUTOMATION", initiator, null)`
  - `SCENARIO` → `scenarioRun.run(projectId, scenarioId, "AUTOMATION", initiator)` (returns the parent SCENARIO run)
- Deps: `RunRepository`, `DataSourceRepository`, `ScenarioRepository` (for labels),
  `RuntimeController`, `DataSourceService` **or** `runtime` for stop, `ReplayService`,
  `SyntheticRunService`, `ScenarioRunService`, `ObjectMapper`.

### 3. Thread `trigger`/`initiator` — Replay/Synthetic
- `ReplayService.replay(..., String trigger, String initiator, String parentRunId)`;
  existing 5-/6-arg overloads delegate with `"MANUAL"`/`"local"`. The `runs.create`
  call uses `trigger`/`initiator` instead of the hardcoded literals.
- `SyntheticRunService.run(..., String trigger, String initiator, String parentRunId)`;
  existing 3-/4-arg overloads delegate with `"MANUAL"`/`"local"`.
- Existing callers (`ReplayController`, `SyntheticRunController`, scenario steps)
  keep working via the delegating overloads. `parentRunId` stays as IS-124 added it.

### 4. API — `RunController` (`/api/v1/projects/{projectId}/runs`)
| Method | Path | `@PreAuthorize` | Result |
|---|---|---|---|
| GET | `/runs` | `OBSERVE` | `Page<RunResponse>` (cursor,limit) |
| GET | `/runs/{id}` | `OBSERVE` | `RunResponse` |
| GET | `/runs/{id}/state` | `OBSERVE` | `RunStateResponse` (runState + per-source states) |
| POST | `/runs/{id}/stop` | `REPLAY_STOP` | `RunResponse` (stopped) |
| POST | `/runs` | `REPLAY_START` | 201 + `RunResponse` |

`POST /runs` body `StartRunRequest(String kind, String initiator, String
dataSourceId, String recordingId, Long durationMs, String scenarioId, Long seed,
String startTime, Boolean compatibilityAck)`. Validation (→ 400
`IllegalArgumentException`): `kind` ∈ {REPLAY,SYNTHETIC,SCENARIO}; `initiator`
non-blank (automation, never anonymous); required fields per kind present
(`REPLAY`: dataSourceId+recordingId; `SYNTHETIC`: dataSourceId+durationMs>0;
`SCENARIO`: scenarioId). DTOs (`RunResponse`, `RunStateResponse`,
`SourceStateResponse`, `StartRunRequest`) nested in the controller, mirroring
`ScenarioController` style. `RunResponse.from(RunView)`.

Permissions: reuse the per-controller `@PreAuthorize` constant pattern
(`Permission.OBSERVE`/`REPLAY_START`/`REPLAY_STOP`). The single `/runs` and
`/runs/{id}/stop` endpoints use one permission each (run kind isn't known
pre-load); `REPLAY_START`/`REPLAY_STOP` are the user-level runtime-operate
permissions — acceptable for the unified endpoint.

## Error handling

`ResourceNotFoundException` (404) for missing run/project; `IllegalArgumentException`
(400) for bad `POST /runs` body; the routed services keep their own errors
(e.g. `SchemaVersionMismatchException` → 409 for a replay compat mismatch,
`FeatureNotAvailableException`/`ScenarioInvalidException` for scenario). Stopping an
already-terminal run is not an error (idempotent: stop sources, leave state).

## Testing (TDD)

- **Persistence IT:** `findByProjectPaged` (newest-first, cursor).
- **`RunService` unit** (Mockito): `listPaged` (enriched, paged); `get` 404 wrong
  project; `stateOf` (run state + per-source health aggregation); `stop` (stops
  each source + STOPPED when non-terminal; terminal → sources stopped, state kept);
  `start` routes REPLAY/SYNTHETIC/SCENARIO to the right service with
  `trigger=AUTOMATION`+initiator; `start` validation (bad kind, blank initiator,
  missing per-kind fields → IllegalArgumentException).
- **Replay/Synthetic:** a test that the new `trigger`/`initiator` overload sets them
  on the created run (existing default-MANUAL/local behavior unchanged).
- **`RunControllerTest` (POJO):** each endpoint's response mapping + status;
  `POST /runs` validation. (HTTP status via `GlobalExceptionHandler` deferred to
  IS-121's MockMvc harness.)

## Out of scope

Merging `/active-runs` into `/runs` (IS-122 untouched); a nested parent/child runs
view (`parentRunId` exposed as a field); a runs SSE stream (separate
`/runs/{id}/stream`); fixing the scenario-run permission inconsistency
(REPLAY_START vs SCENARIO_RUN_START); pausing/resuming runs.

## Definition of done

`./gradlew build` green (incl. the new run IT + unit tests + app context-boot);
IS-089 checkbox flipped `[x]` in `backend-specs/TASKS.md` in the PR; board → In
review via `/open-pr`.
