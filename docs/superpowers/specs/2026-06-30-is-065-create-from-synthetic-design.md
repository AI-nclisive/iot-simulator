# IS-065 — Create from synthetic setup (design)

**Task:** IS-065 [BE] · Wave D — Creation/reuse breadth & synthetic generation · P1
**Issue:** [#86](https://github.com/AI-nclisive/iot-simulator/issues/86)
**Owning spec:** `backend-specs/06_ARTIFACT_FORMATS.md` (Synthetic generation model) + `03_DOMAIN_MODEL.md` (DataSource basis/runtimeConfig); supports `SPEC.md` → *Generate Synthetic Data* / *Manually Create Data Source Schemas*
**Branch:** `feat/IS-065-create-from-synthetic`

## Problem

The synthetic-generation **engine** exists (IS-062/063/064): `SyntheticPattern`
(8 patterns), `SyntheticVariable`, `SyntheticGenerator`, and the determinism
foundation (`DeterministicSettings` → `DeterminismContext` = `MutableClock` +
`SeededRng`). But it is a pure in-memory calculator: nothing creates a
`SYNTHETIC` data source, and nothing feeds generated values into a running
source. `DataSource.runtimeConfig` is stored as opaque JSON and never read.

IS-065 closes that gap end to end: create a `basis=SYNTHETIC` source from a
synthetic setup, and run it so it emits values — the synthetic twin of the
recorded-data path (create-from-scan IS-043 + replay IS-028).

## Key architectural facts that shaped this design

1. **The generator is tick-based and time-independent.** The clock is read
   **once** at construction to fix `start`; sample *n* then has
   `sourceTime = start + n·updateRateMs` arithmetically. Generating *N* samples
   is fully deterministic and independent of wall-clock time.
2. **The worker applies values immediately.** `applyValues` does
   `node.setValue(...)` per value as received — it does **not** play values back
   over wall-clock time by `sourceTime`.
3. **Replay (IS-028) is fire-and-return, not a live stream.** `ReplayService`
   reads the whole timeline and pushes it in one `runtime.applyValues` call; no
   scheduler. `ARCHITECTURE.md` makes **no client delivery-timing guarantees**
   (real timing is the deferred IS-069).

Consequence: the honest, architecture-consistent runtime feed for synthetic is
the **same shape as replay** — generate a bounded deterministic batch and apply
it in one shot (**Model A**). A continuous wall-clock-paced scheduler (**Model
B**) is a larger, cross-cutting supervisor change deferred to **IS-119** (low
priority; pairs with IS-069 real-time pacing for both replay and synthetic).

## Scope

In scope (backend only):

- **Create-from-synthetic** (twin of `ScanService.createFromScan`): persist a
  `basis=SYNTHETIC` source — derive a schema from the synthetic variables and
  store the serialized synthetic config in `runtimeConfig`.
- **Run-synthetic / Model A** (twin of `ReplayService.replay`): generate a
  bounded, deterministic batch and apply it via `runtime.applyValues`, opening a
  `SYNTHETIC` `RunRow` + `Evidence`.
- A serialized synthetic-config format (`06` "Synthetic generation model").
- Validation + `ProblemDetail` error mapping (reusing IS-032).
- Tests (unit, determinism, controller).

Out of scope (explicit, not oversights):

- **Model B — continuous live scheduler / real-time pacing.** A background
  per-source generation loop paced on each variable's `updateRateMs`, running
  until `stop`. This touches supervisor lifecycle (threads, stop/teardown,
  interaction with IS-061 caps/backpressure, health) and weakens pure
  tick-determinism. Tracked as **IS-119** (low priority).
- **Frontend alignment.** The current synthetic wizard (UI-037) is a mock-driven
  stub (`profile + parameterCount + range + interval + seed`, posting to the
  generic create endpoint). Wiring it to the new endpoints with the richer
  per-variable contract is a separate INT-stage UI task, not IS-065.
- **DB migration.** None needed: `basis` already allows `SYNTHETIC` (V1) and
  `runs.kind` already allows `SYNTHETIC` (V3).

## Serialized synthetic config (stored in `runtimeConfig`)

The `06` "Synthetic generation model (serialized)". A dedicated Jackson 3
(`tools.jackson.*`) DTO in the `domain` `synthetic` package — the domain sealed
`SyntheticPattern` interface is **not** annotated for JSON; the DTO is the stable
storage/wire format and maps to/from the domain types.

```
SyntheticConfig {
  Long seed,                              // null => non-deterministic run
  List<SyntheticVariableConfig> variables // size >= 1
}
SyntheticVariableConfig {
  String   nodeId,        // identifier of the schema VARIABLE node
  DataType dataType,
  PatternSpec pattern,    // discriminated by `type`; carries pattern params
  long     updateRateMs   // > 0
}
PatternSpec {
  String type,            // CONSTANT | RAMP | SINE | SQUARE | RANDOM_WALK
                          // | RANDOM_UNIFORM | ENUM_CYCLE | STEP_SEQUENCE
  ... pattern-specific params (e.g. min/max, period, step, values[]) ...
}
```

A `SyntheticConfigMapper` converts `SyntheticConfig` ⇄ domain
`List<SyntheticVariable>` (each `PatternSpec` → the matching `SyntheticPattern`
record). Unknown `type` / invalid params → `IllegalArgumentException`.

## Create-from-synthetic flow

`SyntheticSourceService.create(projectId, name, protocol, endpoint, SyntheticConfig, actor)`
— mirrors `ScanService.createFromScan`:

1. Validate: `variables` non-empty; `nodeId`s unique; `updateRateMs > 0`;
   `dataType` supported (domain already rejects `BYTES`/`DATETIME`); pattern
   params valid (via the mapper).
2. Build schema nodes from variables — one `VARIABLE` `SchemaNode` per variable
   (`nodeId`, `dataType`).
3. `dataSources.create(... basis="SYNTHETIC", runtimeConfig=json(config))`.
4. `schemas.save(projectId, sourceId, nodes)`.
5. Return `dataSources.get(projectId, sourceId)` (with linked `schemaId` /
   `schemaVersion`).

**REST:** `POST /api/v1/projects/{projectId}/data-sources/synthetic`
→ `201 Created` + `DataSourceResponse`.

## Run-synthetic flow (Model A)

`SyntheticRunService.run(projectId, dataSourceId, durationMs)` — copies the
guarded structure of `ReplayService.replay`:

1. Load source; require `basis == SYNTHETIC`; parse `runtimeConfig` →
   `SyntheticConfig`.
2. Open a `SYNTHETIC` `RunRow` + `Evidence`; `runs.start`, link evidence.
3. Build `DeterministicSettings(seed, startTime = clock.instant())` →
   `DeterminismContext`; per variable build a `SyntheticGenerator`.
4. **Horizon (a = `durationMs`):** for each variable
   `N = durationMs / updateRateMs` ticks; collect `generator.next()` N times;
   merge all variables' values into one `List<NeutralValue>` ordered by
   `sourceTime`.
5. `runtime.start(dataSourceId, RuntimeStartSpecs.of(schemas, source))` then
   `runtime.applyValues(dataSourceId, values)`.
6. `runs.end(COMPLETED)` on success / `FAILED` on any exception; return
   `SyntheticRunSummary(dataSourceId, valueCount, runId, evidenceId)`.

**REST:** `POST /api/v1/projects/{projectId}/data-sources/{dataSourceId}/run-synthetic`
body `{ "durationMs": <long> }` → `SyntheticRunResponse`
(`dataSourceId, valueCount, runId, evidenceId`).

The injectable clock comes from the determinism foundation (IS-064), so tests
pin `startTime` and assert reproducibility.

## Error handling

Reuses the `ProblemDetail` mapping (IS-032):

- `IllegalArgumentException` → **400**: empty/duplicate variables, unsupported
  `dataType`, invalid pattern params, `durationMs <= 0`, run-synthetic on a
  non-`SYNTHETIC` source, `runtimeConfig` that fails to parse.
- `ResourceNotFoundException` → **404**: unknown project / data source.

## Persistence

No schema change. `runtime_config jsonb` already exists; `basis` and `runs.kind`
already permit `SYNTHETIC`. `runtimeConfig` is stored/echoed as a JSON string
exactly like `endpoint` today (`JooqDataSourceRepository`).

## Testing (TDD)

- **Unit — config:** `SyntheticConfig` JSON round-trip; `SyntheticConfigMapper`
  ⇄ domain for every `PatternSpec.type`; mapper rejects unknown type / bad params.
- **Unit — create:** `SyntheticSourceService.create` builds the expected schema
  nodes, stores the serialized config, sets `basis=SYNTHETIC`; validation
  failures throw `IllegalArgumentException`.
- **Determinism:** `SyntheticRunService.run` with a fixed seed + pinned clock
  produces an identical applied series across two runs; `applied` count equals
  `Σ (durationMs / updateRateMs)` over variables; run ends `COMPLETED`; failure
  path ends `FAILED`.
- **Controller (mock service):** `201` create, `400` invalid create, `200`
  run-synthetic, `400` run on non-synthetic source.
- Persistence `runtimeConfig` round-trip is already covered by
  `DataSourceRepositoryIT`.

## New / changed artifacts

- **domain** (`domain/.../synthetic/`): `SyntheticConfig`,
  `SyntheticVariableConfig`, `PatternSpec`, `SyntheticConfigMapper`;
  `SyntheticSourceService`; `SyntheticRunService` + `SyntheticRunSummary`.
- **api** (`api/.../datasource/` + `.../synthetic/`):
  `SyntheticSourceController` (create), `SyntheticRunController` (run) — request
  / response records.
- **persistence:** unchanged.
- **catalog / board:** flip IS-065 done in the implementing PR (catalog-sync
  gate); add **IS-119** `[runtime] Run synthetic source — continuous live feed
  (Model B / real-time pacing)` to `TASKS.md` (Wave D, low priority) and mirror
  it to the board.

## Follow-up task to create

**IS-119 [BE] [runtime] Run synthetic source — continuous live feed (Model B).**
Background per-source generation loop paced on each variable's `updateRateMs`,
pushing values until `stop`; reconciles tick-determinism with wall-clock pacing;
integrates with IS-061 caps/backpressure and source health. Low priority; pairs
with IS-069 (replay timing/ordering) as the shared "real-time pacing" capability.
