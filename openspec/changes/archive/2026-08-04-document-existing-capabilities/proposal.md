## Why

The project is adopting OpenSpec as its single spec-driven-development process,
replacing `backend-specs/*.md`, `frontend/docs/{DESIGN,UI_SCREEN_SPECS}.md`,
`SPEC.md`, and the `.superpowers/sdd/` brief/report ledger. Those docs have
drifted from the implementation (e.g. `SPEC.md` marks every epic "ToDo" though
most are shipped; `backend-specs/04_DB_SCHEMA.md` lists 6 migrations against 18
real ones; `docs/FRONTEND_BACKEND_CONTRACT_MAP.md`'s gap index names controllers
that already exist). Before any future change can use openspec's
propose -> apply -> archive flow, the current true system behavior needs a
baseline in `openspec/specs/`.

## What Changes

- Establish baseline specs for the 8 backend capabilities and 2 frontend
  capabilities listed below, written against the **current code**, not the
  stale doc text.
- No application behavior changes - this is a documentation-format migration.

## Capabilities

### New Capabilities
- `protocol-model`: the protocol-neutral node/schema/value model shared by all
  workers (folders, variables, objects, methods, custom data types, array
  dimensions, type references).
- `worker-contract`: the gRPC `ProtocolDataSource` contract and loopback IPC
  between the supervisor and out-of-process protocol workers.
- `domain-model`: core domain entities (Project, DataSource, Schema, Recording,
  Scenario, Evidence, edit leases) and how faults are actually represented
  (inline `ScenarioStep` params, not a separate `Fault` entity).
- `db-schema`: the Postgres schema as Flyway migrations actually define it today.
- `api-contract`: the REST API surface as `@RestController` classes actually
  expose it today (paths, methods, auth requirements).
- `artifact-formats`: recording/evidence/project export-import artifact formats.
- `module-structure`: the Gradle multi-module layout and dependency direction
  rule ("adding a protocol means adding a worker, not changing the supervisor").
- `auth-modes`: local vs shared runtime mode and the OIDC/JWKS-backed security
  model.
- `frontend-shell`: navigation, routing, and shared UI patterns (edit-lock
  banners, role-aware actions), including the `Manual Schemas` surface and
  `Activity` route missing from the old `DESIGN.md` nav list.
- `frontend-screens`: the actual page/screen inventory, including `Manual
  Schemas` (undocumented in the old `UI_SCREEN_SPECS.md` despite being fully
  shipped).

### Modified Capabilities
(none - these are all new baseline specs)

## Impact

Deletes `backend-specs/{01..08}_*.md`, `SPEC.md`,
`frontend/docs/{DESIGN,UI_SCREEN_SPECS}.md`, and `docs/FRONTEND_BACKEND_CONTRACT_MAP.md`
once their content is captured here. No code changes. Follow-up changes handle
`.claude/skills/*`, `TASKS.md`/`UI_TASKS.md`, and the CI `catalog-sync` gate.
