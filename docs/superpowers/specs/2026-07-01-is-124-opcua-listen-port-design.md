# IS-124 — OPC UA listen port from data-source + port uniqueness (design)

**Task:** IS-124 [BE] · P2 · backend enabler
**Issue:** [#337](https://github.com/AI-nclisive/iot-simulator/issues/337)
**Owning spec:** `backend-specs/03_DOMAIN_MODEL.md` (§DataSource — endpoint/runtimeConfig) + `02` (worker listen port)
**Branch:** `feat/IS-124-opcua-listen-port`
**Unblocks:** IS-123 (`/run-local` supervisor mode — turnkey local OPC UA e2e).

## Problem

`RuntimeStartSpecs.of(...)` hardcodes the worker listen port to `0` (ephemeral) —
line 26 even carries the TODO `listen port: ephemeral for now (TODO: from endpoint
config)`. The worker binds an OS-assigned port and the bound endpoint
(`opc.tcp://127.0.0.1:<port>/iotsim`) is never surfaced (not logged, not in IPC
responses, not in the API). So nothing can know where a simulated OPC UA source
bound — an external OPC UA client has no address to connect to, which blocks
IS-123.

This makes the listen port an **input** read from the data source (deterministic,
known-ahead endpoint) and enforces **port uniqueness** so two running sources
can't claim the same port.

## Decisions (agreed in brainstorming)

1. **Listen port lives in `runtimeConfig.listenPort`** (a JSON int), NOT in
   `endpoint`. Rationale: `03 §DataSource` nominally says `endpoint` is the listen
   config, but the codebase already stores the **real** scanned server URL in
   `endpoint` for scan-created sources (`ScanService.createFromScan` →
   `create(..., endpoint, ...)`). Parsing a listen port from `endpoint` would
   collide with that. `runtimeConfig` already carries run-time config (synthetic
   config, replay binding) and is the unambiguous home. Absent/blank/invalid/≤0 →
   `0` (ephemeral) — preserves today's behavior.
2. **Port uniqueness is host-wide, enforced at start.** A TCP bind is host-wide,
   so uniqueness spans all projects. `DataSourceService.start` rejects starting a
   source on a port already held by any RUNNING source (any project). Ephemeral
   (`0`) skips the check. A STOPPED source configured with the same port does not
   block (nothing is bound yet).

## Components

### 1. Read the listen port — `RuntimeStartSpecs.of`
`domain/.../datasource/RuntimeStartSpecs.java` parses `source.runtimeConfig()`
(JSON) for an integer `listenPort` and passes it to `RuntimeStartSpec` instead of
the hardcoded `0`. Absent/blank/unparseable/≤0 → `0`.

Mechanics: `RuntimeStartSpecs.of` is a static helper with no `ObjectMapper`. It
gains an `ObjectMapper` parameter (its three callers — `DataSourceService.start`,
`ReplayService`, `SyntheticRunService` — all already hold one and pass it
through). A small private `listenPort(String runtimeConfig, ObjectMapper json)`
does the tolerant parse (return `0` on any problem, mirroring how
`ScenarioService`/`ScenarioRunService` read JSON fields defensively).

### 2. Uniqueness at start — `DataSourceService.start`
Before `runtime.start(...)`:
- Parse the target source's `listenPort` (same helper).
- If `listenPort != 0`: enumerate all sources via a new
  `DataSourceRepository.findAll()`, and for each whose
  `runtime.state(id) == RUNNING`, parse its `listenPort`; if any equals the
  target's → throw `PortInUseException`.
- Otherwise proceed. Then `runtime.start(id, RuntimeStartSpecs.of(...))`.

(The target itself, if already RUNNING, is excluded by id so a re-start of the
same source is not a self-conflict.)

### 3. New elements
- `DataSourceRepository.findAll()` (+ `JooqDataSourceRepository` impl) — host-wide
  enumeration. Ordered deterministically (e.g. `created_at`, `id`).
- `PortInUseException` in `com.ainclusive.iotsim.domain.common` → mapped to
  **409 CONFLICT** in `GlobalExceptionHandler` (beside `ConcurrencyConflictException`).

## Error handling

`PortInUseException` (carries the port + conflicting source id) → 409 with a clear
message. Malformed `runtimeConfig` never throws from the port parse — it degrades
to `0` (ephemeral), so a bad config can't break `start`.

## Testing (TDD)

- **`RuntimeStartSpecs` unit:** `listenPort` parsed from `runtimeConfig`; absent →
  0; unparseable → 0; ≤0 → 0; valid → passed into the spec.
- **`DataSourceService.start` unit** (in-memory fakes + a `RuntimeController` fake
  with controllable per-source state):
  - start with a free fixed port → succeeds and the port reaches the
    `RuntimeStartSpec` handed to `runtime.start`;
  - start on a port already held by a RUNNING source → `PortInUseException`;
  - the RUNNING source is in a **different project** → still conflicts (host-wide);
  - ephemeral (`0`) → never conflicts (two sources with no port both start);
  - a STOPPED source configured with the same port → does not block;
  - re-starting the same source (same id) → not a self-conflict.
- **Repository IT** (Testcontainers): `findAll()` returns sources across projects.

## Out of scope

Port-RANGE requests (spec allows "port or port-range request" — defer),
auto-surfacing an ephemeral bound port back through IPC/API, the `/run-local`
tooling (IS-123, builds on this), moving the real scanned URL out of `endpoint`
into `connectionConfig` (pre-existing modeling question).

## Definition of done

`./gradlew build` green (unit + ITs + ArchUnit); IS-124 catalog line added and
checked in `backend-specs/TASKS.md` in the same PR; board → In review via
`/open-pr`.
