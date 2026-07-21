# Domain Model (DRAFT)

Status: **DRAFT — proposal for approval.** Concrete entities and relationships
behind the capabilities in `SPEC.md`, built on the protocol-neutral model
(`01_PROTOCOL_NEUTRAL_MODEL.md`). Glossary terms come from `MEMORY.md`. Storage
mapping is in `04_DB_SCHEMA.md`; API exposure in `05_API_CONTRACT.md`.

Conventions: every mutable entity has `id` (ULID), `createdAt`, `updatedAt`,
`createdBy` (principal; "local" in trusted local mode), and `version` (bigint,
optimistic concurrency — D4).

## Entity overview

```
Project 1─* DataSource 1─1 Schema (versioned) 1─* SchemaNode
Project 1─* ManualSchema (reusable, not bound to a DataSource)
Project 1─* Recording 1─* Sample
Project 1─* Scenario 1─* ScenarioStep
Project 1─* Run 1─0..1 Evidence
Run *─* DataSource (sources involved)
DataSource/Run 1─* RuntimeEvent     (runtime stream)
Project 1─* ActivityEvent           (audit stream — separate)
DataSource 1─* ClientConnection
Project 1─1 ProjectSettings ; Environment 1─1 EnvironmentSettings
User/Identity *─* Role ─* Permission
Fault: reusable definition, referenced by ScenarioStep or ad-hoc injection
```

Runtime events and user-activity audit are **distinct streams**
(`ARCHITECTURE.md`) and never merged.

## Entities

### Project
The workspace boundary grouping a simulator setup and reusable artifacts.
- `name`, `description`, `status` (`ACTIVE | ARCHIVED`), `settingsId`.
- Lifecycle: create, rename, duplicate, archive, delete, import, export
  (SPEC "Manage Simulator Projects", "Import And Export").

### DataSource
A simulated instrument source.
- `protocol` (`OPC_UA | MODBUS_TCP`), `basis` (`SCAN | MANUAL | IMPORT | SYNTHETIC`),
  `schemaId` + `schemaVersion`.
- `endpoint` (protocol listen config: host binding, port or port-range request),
  `connectionConfig` (for scan/record of a *real* source; secrets never
  persisted — see `08_AUTH_AND_MODES.md`).
- `runtimeConfig`: replay binding (recording/sample ref), synthetic config, or
  none; deterministic settings.
- `enabled` (bool); `runtimeState` (derived: `STOPPED | STARTING | RUNNING |
  ERROR | STALE`) — owned by the supervisor, not persisted as truth.
- Lifecycle: add, start, stop, remove, duplicate (SPEC "Manage Data Sources On
  Demand"); create-from-scan, manual, import, synthetic.

### Schema / SchemaNode
The protocol-neutral structure of a data-source. Versioned; immutable once a
recording references a version (`01_…`). `SchemaNode` per `01_…` §1. A schema
belongs to one data-source (reuse across sources happens via import/duplicate).

### ManualSchema
A project-scoped, protocol-scoped, standalone reusable structure artifact
(folders + typed variables, no values) — symmetric to `Recording`, but for
structure instead of values (SPEC "Manually Create Data Source Schemas").
- `name`, `description`, `protocol`, `nodes[]` (`SchemaNode` per `01_…` §1).
- Not bound to a data-source. Consumed only as the parameter set for a
  **synthetic** source: its nodes are copied by snapshot into the new source's
  own `Schema` at create time (mirrors the existing `schemaFromSourceId` copy
  path — `IS-145`), so a later edit to the `ManualSchema` never affects an
  already-created source.
- Save model: **save-in-place or save-as-new**, prompted whenever the editor
  has unsaved changes — no monotonic version chain like `Schema`.
- Lifecycle: create, edit (full schema editor), duplicate (save-as), delete.

### Recording
Captured real data over time.
- `dataSourceId`, `schemaVersion` (captured against), `origin`
  (`SCAN_RECORD | IMPORTED`), `timeRange`, `valueCount`, `sizeBytes`, `tags[]`.
- Value data lives in the value-timeline store (`04_…`); large exported blobs in
  ObjectStore.
- SPEC "Record Real Data", "Store Multiple Recordings And Samples".

### Sample
A named reusable subset/snapshot derived from a recording or defined manually.
- `derivedFromRecordingId?`, `selection` (node subset + time window), `name`,
  `tags[]`.

### Scenario / ScenarioStep
A test flow (SPEC "Build Custom Scenarios").
- Scenario: `name`, `status` (`DRAFT | READY | INVALID`), `deterministicSettings`.
- Step `type`: `START | STOP | REPLAY | SYNTHETIC | FAULT | WAIT | MARKER`
  (matches `frontend/docs/UI_TASKS.md` UI-062), `order`, `targetSourceId?`, `params` (typed per
  step type — see `06_ARTIFACT_FORMATS.md`).

### Fault
A simulated unreliable condition (SPEC "Simulate Faults").
- `kind` (`BAD_VALUE | MISSING_VALUE | DELAY | CONNECTION_DROP | TIMEOUT |
  PROTOCOL_ERROR | SOURCE_UNAVAILABLE`), `layer` (`NEUTRAL | PROTOCOL`),
  `target` (source/node), `params`, `intent` (always intentional → never
  auto-healed, per `ARCHITECTURE.md`). Reusable; referenced by a `FAULT` step or
  injected ad-hoc.

### Run
A runtime execution (manual or automated).
- `kind` (`REPLAY | SYNTHETIC | SCENARIO | RECORDING`), `trigger`
  (`MANUAL | AUTOMATED`), `initiator` (principal or automation label — automation
  must never look anonymous, per `frontend/docs/UI_SCREEN_SPECS.md`),
  `state` (`QUEUED | RUNNING | STOPPED | FAILED | COMPLETED`), `startedAt`,
  `endedAt`, `sourceIds[]`, `scenarioId?`, `evidenceId?`.

### Evidence
Portable proof of what happened in a run (SPEC "Export Run Evidence", P0).
- `runId`, `status` (`CAPTURING | READY | PARTIAL | EXPORT_FAILED`), content
  references: value timelines, client connection history, scenario metadata,
  runtime events, faults, errors. Export format in `06_…`. Never contains
  secrets/PKI.

### RuntimeEvent
Runtime stream: source start/stop, client connect/disconnect, replay start/stop,
scenario step changes, faults (SPEC "Observe Runtime Event History"). Linked to
source and/or run.

### ActivityEvent (audit)
User/automation action audit: `actor`, `action`, `objectType`, `objectId`,
`projectId`, `timestamp` (SPEC "Observe User Activity History"). Separate stream
from RuntimeEvent.

### ClientConnection
Observed Edge Device/client per source: `sourceId`, `clientId`, `connectedAt`,
`disconnectedAt?`, activity summary (SPEC "Observe Connected Clients"). Current +
history.

### User / Identity, Role, Permission
- Shared mode: identity = OIDC subject + claims; `Role` maps to a `Permission`
  set; externally `admin`/`user` (D2 flexible model). `status` (active/…).
- Local trusted mode: a single implicit principal with full control, no login
  (SPEC "Use Product Without Login").

### Settings
- `ProjectSettings`: defaults, retention, metadata (project scope).
- `EnvironmentSettings`: deployment-mode, identity provider config refs, retention
  policy, storage config (environment scope). Clearly separated in the UI
  (`frontend/docs/UI_SCREEN_SPECS.md` Settings).

## Notes

- Persisted truth vs runtime truth: entity records are persisted; live
  `runtimeState`, client lists, and live values are owned by the supervisor and
  streamed (SSE) — the DB stores history, not the live snapshot of truth.
- Shared editing: optimistic `version` + advisory edit-lease (see
  `08_AUTH_AND_MODES.md`) back the UI read-only/locked states.
