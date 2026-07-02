# IS-127 — Data-source payload redesign: `simulatorPort` + `realDeviceEndpoint` (design)

**Task:** IS-127 [BE] · payload / domain-model redesign (+ UI-115 [FE] for the UI half)
**Issue:** created at task claim via `/start-task` (board-sync mirrors it to Project #1)
**Owning spec:** `backend-specs/03_DOMAIN_MODEL.md` (§DataSource — endpoint / runtimeConfig)
**Branch:** `feat/IS-127-datasource-payload-redesign` (new, off `master`)
**Supersedes decisions in:** IS-124 (listen port in `runtimeConfig.listenPort`; the "move the
real scanned URL out of `endpoint`" item IS-124 explicitly deferred is resolved here).
**Deferred follow-up (do NOT do here):** worker external-host bind — workers bind `127.0.0.1`
only; making the bind address configurable so the sim is reachable off-host is its own task,
done after this one.

## Problem

The `endpoint` field of a data source is overloaded with two unrelated meanings, and neither
is wired to what the UI actually collects:

1. **Serve side** — where our *locally-spawned* worker listens. This is driven by
   `runtimeConfig.listenPort` (IS-124), which the UI **never populates**. The worker binds an
   ephemeral OS port; the shown `endpoint` is ignored for serving.
2. **Capture side** — the address of a **real external device**, read by
   `RecordingService.startCapture` (IS-045) to record real data in client mode.

Because the UI writes the port only into the free-text `endpoint` string (e.g.
`opc.tcp://localhost:4840`) and serving reads `runtimeConfig.listenPort` (empty), the
port-uniqueness check added in IS-124 is **dead** for UI-created sources: `listenPort` is
absent → parsed as `0` (ephemeral) → the uniqueness loop is skipped. Observed symptom: two
sources "on 4840" both start, and neither actually serves on 4840.

## Goals / non-goals

**Goals:** one field = one meaning; make the serve port a first-class, user-set value with
enforced uniqueness; derive the client connect URL instead of storing a free-text endpoint;
surface bind failures instead of silently "starting".

**Non-goals:** worker external-host bind (deferred); port-range requests (IS-124 deferred, still
deferred); new API version (stay on `/api/v1`, coordinated FE+BE change — no external consumers);
auto-assigned/pooled ports (rejected in brainstorming — user sets the port).

## Decisions (agreed in brainstorming)

1. **Split `endpoint` into two explicit fields:**
   - `simulatorPort` (int, new first-class column `simulator_port`) — the local serve port of
     our worker. User-set; per-protocol default (`OPC_UA`→4840, `MODBUS_TCP`→502).
   - `realDeviceEndpoint` (String, nullable — renamed column, was `endpoint`) — address of the
     real external device the source mirrors. Set only for SCAN/capture sources; `null` for
     SYNTHETIC/MANUAL.
2. **`listenPort` leaves `runtimeConfig`** and becomes the `simulator_port` column — single
   source of truth. `runtimeConfig` keeps other runtime options.
3. **Serve URL is derived, not stored.** A `serveUrl` is computed from
   `advertised-host + simulatorPort + protocol scheme/path` and returned read-only in API
   responses. Host comes from config `iotsim.simulator.advertised-host` (default `localhost`),
   "fixed per network/deploy".
4. **Port assignment:** user sets `simulatorPort`; the system enforces uniqueness among RUNNING
   sources (fix the IS-124 check to read the new column). No auto-assign.
5. **Bind failures are surfaced:** if a worker cannot bind the requested port, the source
   transitions to `ERROR` with a clear message (not a silent success).
6. **Backfill:** migrate existing `listenPort` out of `runtimeConfig` into the column; **null
   out `real_device_endpoint` for existing SYNTHETIC rows** (a real-device address is
   meaningless for them).

## Components

### 1. Persistence & migration

- **Flyway migration** (via `/flyway-migration`, collision-safe version):
  - `alter table data_sources add column simulator_port int;`
  - backfill `simulator_port` from `(runtime_config->>'listenPort')::int` where present, else the
    per-protocol default (`OPC_UA`→4840, `MODBUS_TCP`→502);
  - `runtime_config = runtime_config - 'listenPort'` (drop the migrated key);
  - `alter table data_sources rename column endpoint to real_device_endpoint;`
  - drop the `not null default '{}'` on that column → make it nullable;
  - `update data_sources set real_device_endpoint = null where basis = 'SYNTHETIC';`
  - `alter column simulator_port set not null` after backfill.
- **`DataSourceRow`:** add `Integer simulatorPort`; rename `endpoint` → `realDeviceEndpoint`
  (now nullable). Update `JooqDataSourceRepository` read/write mapping (the column is now a plain
  `int` + nullable text/jsonb — align the (de)serialization; `real_device_endpoint` may drop the
  JSONB-string quoting since it is now a plain optional scalar — plan verifies).
- **`DataSourceRepository.insert/update`** signatures take `simulatorPort` and
  `realDeviceEndpoint` instead of `endpoint`.

### 2. Domain

- **`RuntimeStartSpecs`:** read the port from `row.simulatorPort()` directly; delete the
  `listenPort(String runtimeConfig, ObjectMapper)` JSON-parsing helper. Update the other build
  sites IS-124 touched — `SyntheticRunService`, `ReplayService` — to read the column.
- **`DataSourceService.create/update`:** replace the `endpoint` parameter with `simulatorPort`
  (default per protocol when null; validate 1..65535) and `realDeviceEndpoint` (optional).
- **`SimulatorUrl` helper** (new, `domain/.../datasource`): `of(Protocol, host, port)` →
  - `OPC_UA` → `opc.tcp://<host>:<port>/iotsim`
  - `MODBUS_TCP` → `modbus.tcp://<host>:<port>`
  The single home of the protocol→scheme/path mapping.
- **`RecordingService.startCapture`:** read `source.realDeviceEndpoint()`; error message →
  `"data source has no real-device endpoint to capture from"`. Capture-from-real applies only to
  sources that have a real-device endpoint; SYNTHETIC output is recorded via the synthetic-feed
  path (IS-119 / UI-112), not via this endpoint. **Plan verifies** no code path captures a
  SYNTHETIC source through the (now nulled) endpoint.

### 3. Port uniqueness — `DataSourceService.start`

- Re-point the existing IS-124 check to `row.simulatorPort()` (drop the `runtimeConfig` JSON
  parse). Host-wide, among sources whose `runtime.state(id) == RUNNING`; equal port →
  `PortInUseException` (already 409). The target source is excluded by id (re-start ≠ conflict).
- Port is always set now (per-protocol default, > 0), so the `if (port != 0)` guard is dropped.
- No DB unique constraint and no hard create-time check: two **stopped** sources may share a port
  (only one runs at a time). Optional non-blocking UI hint only.

### 4. Bind-failure surfacing (worker / supervisor / worker-contract)

- The worker attempts the bind when it starts serving. On failure (e.g. `EADDRINUSE`) it reports
  the failure over the control channel as an error (gRPC error status carrying the reason) rather
  than reporting healthy.
- The supervisor's managed-worker path catches the failed configure/start and transitions the
  source's runtime state to `ERROR` with the bind reason, surfaced through the existing runtime
  state / health surface (IS-048 / IS-053). `DataSourceService.start` reflects the failure to the
  caller instead of returning a phantom "started".
- This closes the TOCTOU gap between the uniqueness check and the actual bind, and covers a port
  held by an external process.

### 5. API DTOs — `DataSourceController`

- `CreateDataSourceRequest`: `{ name, protocol, basis, simulatorPort?, realDeviceEndpoint? }`
  (drop `endpoint`). `simulatorPort` optional in the payload; server applies the per-protocol
  default when null; validate range.
- `UpdateDataSourceRequest`: same fields, all nullable (partial update).
- `DataSourceResponse`: expose `simulatorPort`, `realDeviceEndpoint`, and computed read-only
  `serveUrl`; remove `endpoint`.
- Import/export (`ProjectZipExporter` / `ProjectImportService`) and duplicate
  (`ProjectService`, `DataSourceService.duplicate`): carry the two new fields instead of
  `endpoint`. Export format bumps accordingly (plan handles old-archive read compatibility or
  documents the break).

### 6. Frontend (UI-115)

- **Create wizard** (`create-data-source-wizard-page.tsx`): replace the free-text endpoint input
  with a `simulatorPort` number input, pre-filled with the per-protocol default. Scan-based
  creation puts the scanned address into `realDeviceEndpoint`.
- **Settings tab**: edit `simulatorPort` (integer 1..65535); `realDeviceEndpoint` shown for
  SCAN sources only.
- **Overview tab**: show `serveUrl` prominently ("connect your client here") + `realDeviceEndpoint`
  when present; drop the raw endpoint row.
- **List page / `data-sources-store.ts`**: search filter and request/response types on the new
  fields.

## Error handling

- `PortInUseException` (port + conflicting source id) → 409 (unchanged).
- Invalid `simulatorPort` (out of 1..65535) → 400.
- Bind failure at runtime → source `ERROR` state with the bind reason (§4).

## Testing (TDD)

- **`SimulatorUrl` unit:** URL for `OPC_UA` and `MODBUS_TCP`; host from config.
- **`RuntimeStartSpecs` unit:** port taken from `simulatorPort` column, not `runtimeConfig`.
- **`DataSourceService` unit:** create/update apply per-protocol default and validate range;
  start rejects a port held by a RUNNING source (host-wide, cross-project); stopped same-port does
  not block; re-start of same id is not a self-conflict.
- **Migration IT** (Testcontainers): `listenPort` moved out of `runtime_config` into the column;
  SYNTHETIC `real_device_endpoint` nulled; per-protocol default applied where absent.
- **`RecordingService` unit:** reads `realDeviceEndpoint`; new error message.
- **Bind-failure IT:** two sources on the same port started concurrently / port held externally →
  the loser goes `ERROR` with a message (not silent success).
- **Repository IT:** round-trip of `simulatorPort` + nullable `realDeviceEndpoint`.
- **FE (UI-115):** wizard sends `simulatorPort`; overview renders derived `serveUrl`; typecheck +
  vitest.

## Out of scope / deferred

- **Worker external-host bind** — configurable bind address so the sim is reachable off-host;
  separate task after this (memory: `worker-bind-external-access`).
- Port-range requests; auto-assigned/pooled ports; a new API version.

## Definition of done

`./gradlew build` green (unit + ITs + ArchUnit + Checkstyle/Spotless via full build); FE
`npm` typecheck/build/test green; IS-127 line added+checked in `backend-specs/TASKS.md` and
UI-115 in `frontend/docs/UI_TASKS.md` in the same PR(s); boards → In review via `/open-pr`.
