# domain-model Specification

## Purpose
The domain model defines the entities and relationships behind the product's
capabilities, built on the protocol-neutral model, that the API and
persistence layers operate on.
## Requirements
### Requirement: Project as the workspace boundary
A `Project` SHALL group a simulator setup and its reusable artifacts (data
sources, manual schemas, recordings, scenarios). Projects SHALL support
create, rename, duplicate, archive, delete, import, and export.

#### Scenario: Duplicate copies contained artifacts
- **WHEN** a project is duplicated
- **THEN** the new project gets its own independent copies of the source
  project's data sources, schemas, and scenarios

### Requirement: DataSource lifecycle and identity
A `DataSource` SHALL have a `protocol` (`OPC_UA | MODBUS_TCP`), a `basis`
(`SCAN | MANUAL | IMPORT | SYNTHETIC`), a bound `schemaId`/`schemaVersion`, a
`simulatorPort` (the port it serves on), an optional `realDeviceEndpoint`
(only meaningful for scan/record against a real device; null for synthetic
sources), and a derived, non-persisted `runtimeState`
(`STOPPED | STARTING | RUNNING | ERROR | STALE`) owned by the supervisor.

#### Scenario: Synthetic sources have no real-device endpoint
- **WHEN** a data source's `basis` is `SYNTHETIC`
- **THEN** its `realDeviceEndpoint` is null

#### Scenario: Runtime state is not the persisted truth
- **WHEN** the backend restarts
- **THEN** a data source's persisted record is unchanged, but its
  `runtimeState` is recomputed from the supervisor rather than read back from
  storage

### Requirement: Schema is versioned and scoped to one data source
A `Schema` SHALL belong to exactly one `DataSource` and SHALL be versioned
(monotonic integer); it becomes immutable once a `Recording` references that
version. Reuse across data sources happens only via explicit import/duplicate,
never by sharing a live schema reference.

#### Scenario: Referenced schema version cannot be mutated in place
- **WHEN** a data source's current schema version already has a recording
  referencing it, and the user edits that schema
- **THEN** the edit produces a new schema version rather than changing the
  referenced one

### Requirement: ManualSchema is a standalone, reusable structure artifact
A `ManualSchema` SHALL be project-scoped, protocol-scoped, and independent of
any `DataSource` (folders + typed variables, no values). It SHALL be
consumable only as the parameter set for creating a new synthetic
`DataSource`: its nodes are copied by snapshot into the new source's own
`Schema` at creation time, so a later edit to the `ManualSchema` never affects
an already-created source. Saving SHALL support both save-in-place and
save-as-new.

#### Scenario: Editing a ManualSchema does not affect sources already created from it
- **WHEN** a synthetic data source was created from a `ManualSchema`, and that
  `ManualSchema` is later edited
- **THEN** the already-created data source's own schema is unchanged

### Requirement: Recording captures real data over time
A `Recording` SHALL reference the `protocol` it was captured for (not a
specific `DataSource` instance — a recording can be replayed against any
compatible data source of that protocol), the `schemaVersion` it was captured
against, an `origin` (`SCAN_RECORD | IMPORTED`), a time range, value count,
size, and tags.

#### Scenario: Recording replays against a different compatible source
- **WHEN** a recording captured from data source A (protocol `OPC_UA`) is
  chosen for replay on data source B, also `OPC_UA`
- **THEN** replay proceeds, since a recording binds to a protocol, not to
  the originating source instance

### Requirement: Scenario is an ordered sequence of steps
A `Scenario` SHALL have a `status` (`DRAFT | READY | INVALID`) and an ordered
list of `ScenarioStep`s, each with a `type`
(`START | STOP | REPLAY | SYNTHETIC | FAULT | WAIT | MARKER`) and step-specific
params. A scenario is `READY` only when every step references an
existing/compatible target and its required params are present.

#### Scenario: Missing required param keeps a scenario out of READY
- **WHEN** a `REPLAY` step is added without a recording/sample reference
- **THEN** the scenario's status stays `DRAFT`/`INVALID`, never `READY`

### Requirement: Fault behavior is expressed as step or injection parameters, not a standalone entity
There SHALL NOT be a separate, reusable `Fault` entity/table referenced by id.
Fault behavior (`kind`, `layer`, `target`, `params`) is expressed either
inline as a `FAULT`-type `ScenarioStep`'s params, or as an ad-hoc
`InjectFault` call's payload passed straight to the worker contract. A fault
is always intentional and is never auto-healed by the supervisor.

#### Scenario: Fault definition travels with its scenario step
- **WHEN** a scenario step of type `FAULT` is created
- **THEN** its `kind`/`layer`/`target`/`params` are stored inline on that step,
  not as a reference to a separately-persisted fault record

### Requirement: Run tracks one execution
A `Run` SHALL have a `kind` (`REPLAY | SYNTHETIC | SCENARIO | RECORDING`), a
`trigger` (`MANUAL | AUTOMATED`), an `initiator` that is never anonymous for
automated runs, a `state`
(`QUEUED | RUNNING | STOPPED | FAILED | COMPLETED`), and references to the
data source(s) involved and, optionally, the scenario and evidence produced.

#### Scenario: Automated run is never anonymous
- **WHEN** a `Run` is created with `trigger = AUTOMATED`
- **THEN** its `initiator` carries the automation's label, never blank or
  anonymous

### Requirement: Evidence is portable proof of a run
`Evidence` SHALL reference its `runId`, a `status`
(`CAPTURING | READY | PARTIAL | EXPORT_FAILED`), and content references
(value timelines, client connection history, scenario metadata, runtime
events, faults, errors). Evidence SHALL never contain secrets or PKI
material.

#### Scenario: Evidence export never includes a secret field
- **WHEN** evidence is generated for a run on a data source with saved
  connection credentials
- **THEN** the evidence record has no field carrying that credential, in any
  form

### Requirement: Runtime events and activity (audit) events are separate streams
`RuntimeEvent` (source start/stop, client connect/disconnect, replay
start/stop, scenario step changes, faults) and `ActivityEvent` (user/
automation action audit: actor, action, object, timestamp) SHALL be recorded
as distinct streams and SHALL never be merged.

#### Scenario: An operator action produces an activity event, not a runtime event
- **WHEN** a user stops a data source through the UI
- **THEN** an `ActivityEvent` records the user's action, and a separate
  `RuntimeEvent` records the resulting source state change

### Requirement: Identity depends on deployment mode
In shared mode, identity SHALL be the OIDC subject plus claims, mapped to a
`User` with a `Role` that maps to a `Permission` set (externally `admin`/
`user` today). In local trusted mode, a single implicit principal SHALL have
full control and there is no login.

#### Scenario: First-sight OIDC subject creates a User
- **WHEN** a JWT with a `sub` never seen before authenticates in shared mode
- **THEN** a new `User` record is created mapped to that subject

