# IS-119 — Run synthetic source: continuous live feed (Model B / real-time pacing) (design)

**Task:** IS-119 [BE] · Wave D — Creation/reuse breadth & synthetic generation · P2
**Issue:** [#300](https://github.com/AI-nclisive/iot-simulator/issues/300)
**Owning spec:** `backend-specs/02_WORKER_CONTRACT_AND_IPC.md` (runtime apply path) + `03_DOMAIN_MODEL.md` (run lifecycle) + `05_API_CONTRACT.md` (runs surface); supports `SPEC.md` → *Generate Synthetic Data* / *Run Deterministic Scenarios*
**Branch:** `feat/IS-119-synthetic-continuous-feed`
**Pairs with:** IS-069 (replay real-time pacing) — deferred sibling from the IS-065 design.

## Problem

Model A (IS-065, `SyntheticRunService.run(...)`) generates a **bounded**
deterministic batch from a source's stored `SyntheticConfig` and applies it in
**one shot** via `runtime.applyValues(...)`, opening a `SYNTHETIC` run that
**COMPLETES immediately**. That is the honest, architecture-consistent twin of
replay's fire-and-return path, and it is the correct primitive for a **scenario
step** (inject a batch, then advance to the next step).

But it is a poor model for a human **running a synthetic source** interactively:
they expect to point an OPC UA client at the source and watch values tick over
**wall-clock time**, not receive the whole series in a single burst that ends
instantly. IS-119 adds that continuous, real-time-paced feed (Model B).

## Decision: which surface gets which model

Model A is not replaced — it is the right primitive where a run must complete
synchronously. The split:

- **`SyntheticRunService.run(...)` — unchanged.** Remains the bounded,
  synchronous, deterministic **batch primitive**. `ScenarioRunService`
  (`domain/.../scenario/ScenarioRunService.java:92` — a synchronous step loop
  that *blocks* on the synthetic step returning) and any dataset generation keep
  calling it as-is. No signature change ⇒ no scenario refactor; IS-065 tests
  stay green.
- **New `SyntheticLiveRunService` — the paced feed.** Owns run/evidence
  creation, incremental generation, the pacing loop, and stop/teardown.
- **Standalone surfaces re-routed to live:** `POST .../run-synthetic`
  (`SyntheticRunController`) and `POST /runs {kind:"SYNTHETIC"}`
  (`RunService`/`RunController` dispatcher) now start a **live run** instead of a
  one-shot. This is a deliberate change to shipped semantics (see §7). No
  frontend calls `/run-synthetic` directly, so blast radius is small.

## Key architectural facts that shaped this design

1. **`applyValues` is a fire-and-forget batch push with no timing semantics**
   (`platform/.../runtime/RuntimeController.java`). Pacing must layer **above**
   it — consistent with `ARCHITECTURE.md`'s no-client-delivery-timing stance.
   No supervisor change is required.
2. **The generator is tick-based and time-independent.** `SyntheticGenerator`
   reads the clock once at construction to fix `start`; sample *n* then has
   `sourceTime = start + n·updateRateMs`. The value **sequence** is fully
   seed-deterministic regardless of when samples are pushed.
3. **A run stays RUNNING indefinitely until ended.** `RunRow` states are
   `QUEUED → RUNNING → {STOPPED | FAILED | COMPLETED}`; a long-lived feed is
   just a run held in RUNNING (no separate job/session row needed).
4. **The stop lifecycle already exists.** `POST /runs/{id}/stop` calls
   `runtime.stop()` per source and moves the run to STOPPED. Model B hooks this
   to also cancel its pacing task.
5. **Values pushed via `applyValues` already fan out to SSE live streams**
   (supervisor `applyValues` → `LiveValueListener`), so a paced feed is
   observable in the UI with no extra wiring.
6. **The existing background pattern is `ScheduledExecutorService`** with a
   daemon thread and a fixed-rate tick (`LiveValuesHub` flushes every 250 ms;
   `Supervisor` polls health on a fixed delay). Model B follows this pattern.

## Components

### `SyntheticLiveRunService` (domain)

Responsibilities:

- **Start:** validate the source is `SYNTHETIC`, parse `SyntheticConfig`, build
  `DeterministicSettings` (seed from config or random), create the run
  (`kind="SYNTHETIC"`), `start()` → RUNNING, create + link evidence, write the
  start manifest (`{synthetic:true, seed}`), `runtime.start(...)`, then register
  the run with the pacer and return immediately.
- **Pace:** one `SyntheticGenerator` per variable sharing one
  `DeterminismContext`, plus a per-variable "next tick index". On each pacing
  tick, advance the run's `MutableClock` by the real elapsed wall-clock,
  compute how many samples each variable is now due
  (`floor(elapsedMs / updateRateMs)`), pull only the newly-due samples from the
  generators, sort by `sourceTime` then `nodeId`, and `runtime.applyValues(...)`.
- **Finalize:** on manual stop → `runs.end(id, "STOPPED", now)`; on
  `maxDurationMs` cap → `runs.end(id, "COMPLETED", now)`; on error →
  `runs.end(id, "FAILED", now)`. Update the evidence manifest with the final
  total `valueCount`.

### `SyntheticLivePacer` (the scheduler + registry)

- One shared daemon `ScheduledExecutorService` (thread name `synthetic-live-N`).
- Fixed-rate tick, default **250 ms** (configurable via a runtime property;
  aligned with `LiveValuesHub`).
- **Registry:** `runId → LiveRun` where `LiveRun` holds the `DeterminismContext`,
  the per-variable generators + tick cursors, the wall-clock start, the optional
  `maxDurationMs`, and the `ScheduledFuture`. `stop(runId)` cancels the future,
  removes the entry, and returns whether it was live (idempotent).
- Spring bean with `destroyMethod` that cancels all futures and shuts the
  executor down on context close.

### Wiring changes

- `SyntheticRunController.run(...)` → calls `SyntheticLiveRunService.start(...)`.
- `RunService` `kind="SYNTHETIC"` dispatch → `SyntheticLiveRunService.start(...)`.
  (Scenario dispatch keeps calling `SyntheticRunService.run(...)`.)
- Run stop path (`RunService.stop` / `RunController` `POST /runs/{id}/stop`) →
  also calls `pacer.stop(runId)` before/around `runtime.stop(...)` so the tick
  loop is cancelled and the run is finalized.

## Data flow (one live run)

```
POST /run-synthetic (or POST /runs {kind:SYNTHETIC})
  → SyntheticLiveRunService.start
      create run (RUNNING) + evidence + manifest{seed}
      runtime.start(source)
      pacer.register(runId, generators, clock, maxDurationMs?)
      return { runId, evidenceId, seed, state: "RUNNING" }   ← immediate

pacer tick (every 250 ms), per registered run:
  elapsed = now - startWall
  if maxDurationMs and elapsed ≥ cap → finalize COMPLETED, cancel
  for each variable: dueTicks = elapsed / updateRateMs
      emit generator samples from cursor..dueTicks ; advance cursor
  sort due samples ; runtime.applyValues(source, dueSamples)  → SSE

POST /runs/{id}/stop
  → pacer.stop(runId) (cancel future) ; runtime.stop(source)
  → runs.end(runId, "STOPPED") ; evidence manifest valueCount = total
```

## Determinism

The value **sequence** stays fully seed-deterministic: same seed + config ⇒
identical series and ordering. What varies across runs is **how many** samples
are emitted before a manual stop (a function of wall-clock duration). This is the
expected, documented "weakens pure tick-determinism" trade-off called out in the
IS-065 design for Model B — inherent to a live, stop-controlled feed. `sourceTime`
remains **logical** (`start + n·updateRateMs`), advanced via the injected
`MutableClock`, so timestamps are reproducible for a given emitted count.

## Error handling

- **Not synthetic / missing source / invalid config** → `IllegalArgumentException`
  / `ResourceNotFoundException` at start (same as Model A), before any run row is
  left dangling in RUNNING.
- **`runtime.start` fails** → run ended FAILED, exception surfaced; nothing
  registered with the pacer.
- **A pacing tick throws** (e.g. `applyValues` fails mid-feed) → catch inside the
  tick, finalize the run FAILED, record `lastError` (visible via
  `GET /runs/{id}/state`), and cancel the future. One run's failure must not kill
  the shared scheduler or other runs' ticks.
- **Context shutdown** → `destroyMethod` cancels all futures; in-flight runs are
  left as-is (RUNNING) for restart visibility, matching existing teardown.

## API contract change

`SyntheticRunResponse` (standalone path) now returns `state:"RUNNING"` and
`valueCount` absent/`0` at start — the final count is available on the run /
evidence once ended, and progress is queried via the existing
`GET /runs/{id}/state`. Swagger descriptions updated to say the synthetic run is
a continuous live feed stopped via `POST /runs/{id}/stop` (optional
`maxDurationMs` cap). Request gains an optional `maxDurationMs` (null = unbounded).

## Testing

- **`SyntheticLiveRunServiceTest`** — injected `MutableClock` + a **manually
  pumped pacer seam** (no real sleeps): N ticks ⇒ correct per-variable due-sample
  counts; seed-deterministic sequence; stop cancels & finalizes STOPPED; cap ⇒
  COMPLETED; a throwing tick ⇒ FAILED with `lastError` and no impact on a second
  registered run.
- **Web-layer test** — re-routed `POST /run-synthetic` and `POST /runs
  {kind:SYNTHETIC}` return `state:"RUNNING"` and a `runId`.
- **Stop-path test** — `POST /runs/{id}/stop` cancels the live task and finalizes
  the run.
- **Regression** — existing `SyntheticRunServiceTest` and `ScenarioRunServiceTest`
  stay green (batch primitive untouched).

## Governance & scope

- **SPEC.md**: *Generate Synthetic* / *Run Deterministic* are timing-neutral, so
  no capability is added or removed — but the observable behavior of "run a
  synthetic source" changes from one-shot to continuous. A short clarifying note
  will be proposed to SPEC and confirmed with the user before committing (per
  `AGENTS.md`: no silent SPEC edits).
- **Scope**: slightly wider than IS-119's literal "additive Model B" wording. The
  `backend-specs/TASKS.md` IS-119 line will be updated to reflect "live is the
  standalone run model; batch retained for scenario steps."
- **No new dependencies. No supervisor changes.**

## Out of scope (YAGNI)

- Speed factor / time-warp (pace is strictly 1:1 real-time).
- Per-variable independent scheduling (single shared tick emits all due samples).
- Refactoring `ScenarioRunService` to async (scenario steps keep the batch model).
- Real-time-paced **replay** (that is IS-069's separate task).
