# IS-053 — Source health & error surfacing (design)

**Task:** IS-053 [BE] · Wave C — Observability & evidence · P0
**Issue:** [#74](https://github.com/AI-nclisive/iot-simulator/issues/74)
**Owning spec:** `SPEC.md` → *Observe Data Source Health And Errors*
**Branch:** `feat/IS-053-source-health-error-surfacing`

## Problem

The runtime supervisor already computes a per-source state
(`RUNNING / STARTING / STALE / ERROR / STOPPED`) and exposes it two ways: pulled
via `DataSourceResponse.runtimeState`, and as the initial `runtime-state`
snapshot sent on connect to `GET /api/v1/projects/{pid}/stream/runtime` (IS-051).

The gap: when the supervisor's **own** health loop flips a source
(RUNNING→STALE on missed probes, STALE→recovered, or →ERROR on worker exit /
restart-budget exhausted) it only mutates an internal flag — it emits **no
event**. A client already watching the runtime stream never learns a source went
stale/errored; it only finds out by reconnecting and reading a fresh snapshot.
Worker-emitted `ERROR` runtime events (IS-048) flow through, but
supervisor-detected health transitions do not. There is also no point-in-time
"what is this source's health right now, and what was the last error" read, and
no captured **error detail** (only a binary stale flag).

The SPEC capability stresses *errors*: users must tell whether a problem is the
simulator, the source configuration, the protocol, or the Edge Device.

## Scope

In scope:
- Supervisor emits per-source health **transitions** as runtime events so they
  push live on the runtime SSE stream **and** persist to `runtime_events`
  (riding the existing IS-047/048/049 rail).
- An `origin` attribution (`SIMULATOR / PROTOCOL / UNKNOWN`) and human-readable
  `reason` carried on health/error events and reads.
- A point-in-time `GET /api/v1/data-sources/{id}/health` returning current state
  plus the last error.
- The `runtime-state` connect snapshot enriched with each source's last error.

Out of scope (tracked elsewhere):
- Runtime-event **history** query/pagination — **IS-055** (this task only writes
  the rows; IS-055 reads them).
- Evidence assembly/export — **IS-057/058**.
- A four-way origin taxonomy (source-config / Edge Device as distinct enum
  values) — conveyed via `reason` text here; revisit if a product need appears.

## Decisions

| Decision | Choice | Rationale |
| --- | --- | --- |
| Transport for transitions | Reuse `RuntimeActivityEvent` rail | One emit → `LiveEventHub` streams it AND `PersistingRuntimeActivityListener` writes it to `runtime_events`; consistent with how worker events already work; feeds IS-055 history for free. |
| Persist transitions? | Yes | A health timeline (not just the current instant) is what lets a user diagnose intermittent problems even if they were not watching live. |
| Carry `origin` without migration | Put it in the existing `payload` jsonb | `runtime_events.payload` is already jsonb (`{"detail":…}`); adding `"origin"` needs **no** schema change. |
| Origin values | `SIMULATOR`, `PROTOCOL`, `UNKNOWN` (3) | These are what the supervisor can reliably determine; finer source-config/Edge-Device distinctions live in `reason`. |
| `lastError` after recovery | **Retained** | State returns to `RUNNING`, but the most-recent error stays readable ("last error was X at T") — more useful for diagnosis than nulling it. |
| Point-in-time read layering | `HealthController` calls `RuntimeController.health(id)` directly | Health is a pure pass-through of the runtime port (no repo, unlike `/clients`); a domain service would be an empty layer. |
| Unknown source / in-memory mode | `200` with `{"state":"STOPPED","lastError":null}` | Mirrors the sibling `/clients` and `/stream/*` endpoints; spec 05 does not require 404. |

## Architecture / components

**`platform` (`com.ainclusive.iotsim.platform.runtime`)**
- `HealthOrigin` — `enum { SIMULATOR, PROTOCOL, UNKNOWN }`.
- `SourceError` — `record(HealthOrigin origin, String reason, Instant at)`.
- `SourceHealth` — `record(String state, SourceError lastError)`; `lastError`
  nullable. `state` stays a plain string, matching `RuntimeController.state`.
- `RuntimeController` — new method `SourceHealth health(String dataSourceId)`.
- `RuntimeActivityEvent` — add a 5th component `HealthOrigin origin` (nullable).
  Update the ~2 producers; `origin == null` means "not a health/error event".
- `InMemoryRuntimeController` — `health(id)` → `SourceHealth(state(id), null)`.

**`runtime-supervisor` (`Supervisor.ManagedWorker`)**
- New per-worker `lastError` field (guarded like the other mutable state).
- Emit a `RuntimeActivityEvent` on each **real** transition, outside the monitor
  (as worker-event forwarding already does):
  - probe staleness false→true → `SOURCE_STALE`, origin `SIMULATOR`;
  - stale→clear while RUNNING → `SOURCE_RECOVERED`, origin `SIMULATOR`;
  - worker exit / restart budget exhausted → `SOURCE_ERROR`, origin `SIMULATOR`.
- `toRuntimeActivity` (worker `RuntimeEvents` mapping): worker `ERROR` →
  origin `PROTOCOL`; `SOURCE_START`/`SOURCE_STOP` → origin `null`.
- Implement `health(id)` → `SourceHealth(stateName(), lastError)`.

**`app` (`PersistingRuntimeActivityListener`)**
- `payload(...)` includes `origin` when present:
  `{"detail":"…","origin":"SIMULATOR"}`. No DB migration.

**`api`**
- `LiveEventHub.onRuntimeActivity` — add `origin` to the SSE data map when set.
- `RuntimeStateSnapshot` — `SourceRuntimeState` becomes
  `(dataSourceId, state, SourceError lastError)`; built from
  `runtimeController.health(id)` instead of `state(id)`.
- New `HealthController` (`@RestController`) at
  `GET /api/v1/data-sources/{id}/health`, injecting `RuntimeController`, with a
  nested `SourceHealthResponse` DTO + mapper and the unknown-source default.

## API contract

`GET /api/v1/data-sources/{id}/health` → `200 OK`, `application/json`:

```jsonc
{
  "state": "STALE",                 // RUNNING | STARTING | STALE | ERROR | STOPPED
  "lastError": {                    // null when no error has occurred
    "origin": "SIMULATOR",          // SIMULATOR | PROTOCOL | UNKNOWN
    "reason": "no health response in 3 probes",
    "at": "2026-06-30T12:34:56Z"
  }
}
```

- Unknown `id` (or in-memory mode, never started) → `200` with
  `{"state":"STOPPED","lastError":null}`.
- OpenAPI/Swagger entry is auto-generated by springdoc (IS-030).

SSE `runtime` channel (`GET /api/v1/projects/{pid}/stream/runtime`):
- New event types `SOURCE_STALE` / `SOURCE_RECOVERED` / `SOURCE_ERROR`; the data
  map carries `dataSourceId`, `type`, `at`, `detail`, and `origin`.
- The connect `runtime-state` snapshot: each element gains `lastError`.

## Origin attribution (maps to the SPEC's four problem sources)

| Trigger | type | resulting state | origin | reason (example) |
| --- | --- | --- | --- | --- |
| N consecutive missed health probes | `SOURCE_STALE` | STALE | SIMULATOR | "no health response in N probes" |
| A probe succeeds again | `SOURCE_RECOVERED` | RUNNING | SIMULATOR | "health restored" |
| Worker process exits / restart budget exhausted | `SOURCE_ERROR` | ERROR | SIMULATOR | "worker exited" / "restart budget exhausted after N attempts" |
| Worker emits an `ERROR` runtime event | `ERROR` | (unchanged) | PROTOCOL | the worker's detail text |

The supervisor reliably distinguishes **SIMULATOR** (its own monitoring) from
**PROTOCOL** (worker-reported), with **UNKNOWN** as fallback. The SPEC's
"source configuration" and "Edge Device" nuances are conveyed through `reason`,
not a distinct origin value.

## Data flow

```
transition (health loop / worker exit):
  worker.lastError = SourceError(origin, reason, now)
  runtimeActivityListener.onRuntimeActivity(           // off the monitor
      RuntimeActivityEvent(dsId, SOURCE_*, now, reason, origin))
    → LiveEventHub      → SSE runtime channel of the project (live delta)
    → PersistingRuntimeActivityListener → runtime_events row (payload carries origin)

point-in-time read:
  GET /data-sources/{id}/health → runtimeController.health(id)
                                → SourceHealth(state, lastError)

stream connect:
  RuntimeStateSnapshot → for each source: health(id) → {state, lastError}
```

## Error handling & edge cases

- **Flapping:** emit only on an actual state change (track the last-emitted
  health state per worker); `STALE` is already debounced by the N-missed-probe
  threshold, `RECOVERED` fires on the first good probe after stale.
- **Duplicate ERROR:** a worker-emitted `ERROR` (origin `PROTOCOL`) and a
  supervisor-detected exit (`SOURCE_ERROR`, origin `SIMULATOR`) describe one
  incident from two vantage points; distinct `origin` keeps them distinguishable
  rather than looking like a duplicate.
- **Threading:** emits happen on the health-poll scheduler / exit-callback
  thread, outside the worker monitor; `LiveEventHub` and the persisting listener
  already hop to their own executors.
- **Unknown source / in-memory mode:** `health()` returns `STOPPED` + `null`;
  the endpoint returns `200`.
- **Ordering:** the connect snapshot reflects current health; subsequent
  transitions arrive as deltas (the established IS-051 pattern).

## Testing

- **Supervisor** (unit/IT with the existing fake launcher/worker): missed probes
  emit exactly one `SOURCE_STALE` + set `lastError` + `health()` reflects STALE;
  a good probe emits `SOURCE_RECOVERED` and state returns to RUNNING while
  `lastError` is retained; restart-budget exhaustion emits `SOURCE_ERROR` with
  origin `SIMULATOR`.
- **`toRuntimeActivity`:** worker `ERROR` → origin `PROTOCOL`; `START`/`STOP` →
  `null`.
- **`PersistingRuntimeActivityListener`:** `origin` is written into the `payload`
  jsonb; absent origin keeps the `{}`/`{"detail":…}` shape.
- **`RuntimeStateSnapshot`:** snapshot elements carry `lastError`.
- **`HealthController`** (web/E2E, following `ClientObservationEndToEndTest`):
  returns `state` + `lastError`; unknown `id` → `200` default.
- **`InMemoryRuntimeController`:** `health()` → state + `null`.
- `./gradlew build` green, including Spotless, Checkstyle and ArchUnit module
  boundaries.

## Known trade-off

`lastError` is retained after recovery, so a long-running source can display an
old error alongside a healthy `RUNNING` state. This is intentional (the timeline
in `runtime_events` plus the "last error" pointer aid diagnosis); the state field
remains authoritative for "is it healthy now". `origin` is a coarse 3-value enum
rather than the SPEC's full four-way attribution; the finer distinction is
carried in `reason` and can be promoted to enum values later without a contract
break (additive).
