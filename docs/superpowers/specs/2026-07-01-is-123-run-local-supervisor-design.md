# IS-123 — `/run-local` = supervisor mode (design)

**Task:** IS-123 [SDLC] · P2 · dev tooling
**Issue:** [#333](https://github.com/AI-nclisive/iot-simulator/issues/333)
**Owning artifact:** the `/run-local` skill (`.claude/skills/run-local/SKILL.md`) + README "Configuration"
**Branch:** `feat/IS-123-run-local-supervisor`
**Builds on:** IS-124 (merged — OPC UA listen port from `runtimeConfig.listenPort` + host-wide uniqueness). Backend OPC UA capability already complete (IS-036/037/038 real Milo server, IS-039 process spawn, IS-021 RuntimeController wiring).

## Problem

`/run-local` (IS-120) brings the local stack up in `IOTSIM_RUNTIME_MODE=memory` — the `InMemoryRuntimeController` tracks state but spawns **no worker**, so starting a source shows `RUNNING` with no real OPC UA endpoint. That is useless for the platform's actual purpose (create a source → start it → an edge device connects to a real OPC UA server). The backend fully supports real workers in `supervisor` mode; only the local tooling doesn't wire it.

## Decision (agreed in brainstorming)

**`/run-local` is supervisor-only.** The skill's default (and only) up-path brings the platform up with real out-of-process OPC UA workers. The memory stub is **removed from the skill** — it remains the app's own default (`application.yml`: `IOTSIM_RUNTIME_MODE:memory`, used by tests and reachable by manually exporting the env var), but the convenience skill no longer offers or defaults to it. The skill does NOT create or start any data source — the user does that in the UI; starting a source is what spawns the worker.

Rationale: memory mode's real homes are hermetic tests/CI and UI-only frontend work — neither is what `/run-local` is for. For running the platform to use it, supervisor is always what's wanted; a dual-mode skill only re-introduces the "RUNNING but no endpoint" confusion.

## Behavior — `/run-local` (up)

1. **Preflight** — Docker running (Colima/Desktop), compose command — unchanged.
2. **Build the OPC UA worker:** `./gradlew :workers:worker-opcua:installDist` → produces `workers/worker-opcua/build/install/worker-opcua/bin/worker-opcua` (and `.bat` on Windows). The supervisor launches this per-protocol command to spawn a worker process.
3. **Postgres** — `docker compose up -d postgres` + readiness — unchanged.
4. **Backend in supervisor mode:** launch `:app:bootRun` (background) with:
   - `IOTSIM_MODE=local` (auth off) — unchanged.
   - `IOTSIM_RUNTIME_MODE=supervisor`.
   - the worker command wired via `SPRING_APPLICATION_JSON` env:
     `{"iotsim":{"runtime":{"workers":{"OPC_UA":["<abs path>/worker-opcua"]}}}}`
     (Spring binds this to `iotsim.runtime.workers.OPC_UA[0]`; `RuntimeConfig` builds the real `Supervisor` when `mode=supervisor`.) The exact quoting per shell (bash vs PowerShell) is settled in the plan; `SPRING_APPLICATION_JSON` is chosen over a `-D`/`--args` list index because JSON list binding is unambiguous cross-platform.
   - then poll `/actuator/health` until UP — unchanged.
5. **Frontend** — `npm run dev` (background) + readiness — unchanged.
6. **Verify + report:** the existing proxy check, then report URLs (UI :4173, Swagger, API, health) **plus** a supervisor note: starting an `OPC_UA` source in the UI now spawns a real Eclipse Milo worker that binds the source's `runtimeConfig.listenPort` and serves `opc.tcp://127.0.0.1:<port>/iotsim` — point an external OPC UA client (the edge device under test) there. If a source has no `listenPort`, the port is ephemeral (IS-124) and not surfaced.

## Behavior — `/run-local down`

Unchanged core: stop the backend + frontend background tasks (by task id) and `docker compose stop postgres` (keep the `pgdata` volume). **Added:** also stop any lingering `worker-opcua` processes. Graceful worker shutdown (Shutdown RPC) is IS-090 (Todo), so when the backend is killed its spawned worker processes may survive — the teardown kills them explicitly, cross-platform:
- bash/macOS/Linux: `pkill -f worker-opcua` (best-effort).
- PowerShell/Windows: `Get-Process | Where-Object { $_.Path -like '*worker-opcua*' } | Stop-Process` (best-effort).
Report how many were stopped; tolerate none.

## What changes in the docs

- `.claude/skills/run-local/SKILL.md`: rewrite the intro/description (it brings up the **real supervisor stack**, not the memory stub); add the worker `installDist` step; set the supervisor env on the backend launch; add the worker-kill to teardown; remove the memory-default line and the "Need real protocol workers … not covered here" note (that IS now the path).
- `README.md` "Configuration": keep the `IOTSIM_RUNTIME_MODE` row (memory still documented as the app default / manual option); adjust any `/run-local` reference to say it runs supervisor. `installDist` task is already listed.

## Out of scope

Auto-creating/starting a demo source or synthetic run (the user drives the UI); exposing/setting `runtimeConfig.listenPort` in the UI create form (frontend follow-up — a UI task; without it a UI-created source binds an ephemeral, unsurfaced port); IS-090 graceful worker shutdown; the Modbus worker; changing the app's own default `IOTSIM_RUNTIME_MODE` (stays `memory` for tests/bare runs).

## Verification

The skill is a markdown procedure, not code — there is no gradle build to gate it. DoD is a **manual run of `/run-local`**: the worker `installDist` succeeds, the backend comes up in supervisor mode, and starting an OPC UA source (created in the UI with a `listenPort`) spawns a worker process that binds the endpoint an OPC UA client can connect to; `/run-local down` leaves no lingering worker process. CI `build` passes trivially (no code changed); the PR is docs-only.

## Definition of done

`/run-local` documented + working for supervisor mode; teardown kills workers; IS-123 catalog line flipped `[x]` in `backend-specs/TASKS.md` in the same PR (the line exists on master — registered earlier); board → In review via `/open-pr`.
