## Purpose

The REST/OpenAPI surface (plus SSE for live updates) is the one integration
point automation and the frontend use to command and observe the simulator;
the API layer is where authorization is enforced.

## ADDED Requirements

### Requirement: Versioned base path and optimistic concurrency
Every endpoint SHALL live under `/api/v1`; changes stay additive within the
major version, a breaking change gets `/api/v2`. Mutating endpoints SHALL
support `ETag`/`If-Match` on the entity `version`; a mismatch SHALL return
`409 Conflict` rather than silently overwriting.

#### Scenario: Concurrent edit is rejected, not overwritten
- **WHEN** two clients read the same entity version and both attempt to
  update it
- **THEN** the second update's `If-Match` no longer matches and the API
  returns `409 Conflict`

### Requirement: Errors are RFC 9457 problem+json
Error responses SHALL use `application/problem+json` with a stable `type`
code, so clients can distinguish error kinds programmatically rather than by
parsing prose.

#### Scenario: Client branches on the stable type code
- **WHEN** a request fails validation
- **THEN** the response is `application/problem+json` with a `type` field a
  client can switch on, not just a human-readable message

### Requirement: Projects and data sources are managed via CRUD plus lifecycle actions
`Projects` and `DataSources` SHALL expose standard CRUD endpoints plus
`duplicate`; projects additionally support `archive`, `import` (multipart),
and `export`. A data source has no bare `start` endpoint — starting happens
only through the specific runtime action being started (capture or
simulate, below); `stop` stops whichever runtime action is currently active.

#### Scenario: No bare start
- **WHEN** a client wants to make a data source active
- **THEN** it calls either the recording-start or the replay-start endpoint,
  never a generic `start`

### Requirement: Manual schemas are a separate, source-independent resource
`Manual schemas` SHALL expose CRUD plus `duplicate` (save-as-new) under a
project, independent of any data source. Creating a synthetic data source
SHALL accept a `manualSchemaId` (copying that schema's nodes by snapshot into
the new source), mutually exclusive with `schemaFromSourceId`.

#### Scenario: Manual schema usable without any data source
- **WHEN** a project has a `ManualSchema` but no data sources at all
- **THEN** the manual schema's CRUD endpoints still work, independent of any
  data source existing

### Requirement: Scan is an asynchronous job
Scanning a real endpoint's address space SHALL run as an async job: starting
returns a `jobId`; progress/result is polled, discovered nodes are available
cursor-paginated, and a running job can be cancelled (settling as
`CANCELLED`).

#### Scenario: Cancelling a scan settles it, not silently drops it
- **WHEN** a client cancels an in-progress scan job
- **THEN** the job's terminal state is `CANCELLED`, visible to subsequent
  polls of that job id

### Requirement: Capture (recording) reports whether it is currently running
Starting a recording from a real source SHALL create a `Recording` and drive
a worker in client mode against `realDeviceEndpoint`. A status endpoint SHALL
report `{capturing, recordingId}` so a stuck or orphaned capture is
discoverable rather than only surfacing as a rejected start.

#### Scenario: Orphaned capture is discoverable
- **WHEN** a data source's recording start returns "already capturing" but no
  UI session initiated it
- **THEN** the recording-status endpoint reports `{capturing: true,
  recordingId}` so the stuck capture can be found and stopped

### Requirement: Simulate starts a live run that stays RUNNING until stopped
Starting a replay against a recording/sample SHALL start a `Run` that stays
`RUNNING` until an explicit stop, serving recorded values at their original
pace through the worker.

#### Scenario: Run stays RUNNING until stopped
- **WHEN** a replay run is started successfully
- **THEN** its state remains `RUNNING` until a client calls the run's stop
  endpoint, never stopping on its own once the recording finishes

### Requirement: Automated runs carry a non-anonymous initiator
The automation-facing run endpoints (`start`, `GET .../runs/{id}/state` for
polling, `stop`) SHALL record an automation initiator label; an automated run
SHALL never appear with an anonymous initiator.

#### Scenario: Automated start is attributed
- **WHEN** an automated test suite calls the automation-facing start endpoint
- **THEN** the resulting run's `initiator` carries the automation's label,
  never a blank or anonymous value

### Requirement: Evidence export excludes secrets and supports retry
Exporting evidence SHALL let the caller pick format and scope with secret
exclusion explicit; a failed export SHALL be retryable. The exported bundle
SHALL be downloadable as `application/zip`, returning `404` until an export
has actually produced a blob.

#### Scenario: Failed export can be retried
- **WHEN** an evidence export fails
- **THEN** the same evidence's export endpoint can be called again without
  first deleting or recreating the evidence record

### Requirement: Activity and runtime-event history are distinct, filterable endpoints
Activity (user/automation audit, filterable by actor/action/object/time) and
runtime-event history (filterable by source/run/type/time) SHALL be exposed
as separate endpoints, matching the separate storage streams in the
`domain-model` capability.

#### Scenario: Filtering activity by actor does not return runtime events
- **WHEN** a client calls the activity endpoint filtered by a specific actor
- **THEN** the response contains only `ActivityEvent` records for that actor,
  never `RuntimeEvent` records

### Requirement: Edit leases back shared-editing safety
Acquiring/releasing an advisory edit lease on an object (e.g. before opening
the full schema editor or scenario builder) SHALL be exposed per object type
and id, backing the UI's read-only/locked-for-others state. The lease is
advisory; the authoritative guard against lost updates is optimistic
concurrency (`version`/`ETag`), not the lease.

#### Scenario: Second lease request is rejected while the first holds
- **WHEN** user A holds an active edit lease on a schema and user B requests
  a lease on the same object
- **THEN** B's lease request is rejected/denied until A's lease is released
  or expires

### Requirement: Live updates are one-way SSE streams
Runtime context, per-source values, per-source client connections, and
per-run progress SHALL each be exposed as a one-way SSE stream that clients
reconnect to with `Last-Event-ID`. Streamed values are explicitly the
conflated/throttled path, not the full-fidelity recording path.

#### Scenario: Client resumes after a dropped connection
- **WHEN** an SSE client reconnects after a dropped connection, sending
  `Last-Event-ID`
- **THEN** the stream resumes from that event id rather than replaying
  everything or silently skipping a gap

### Requirement: Role-aware behavior on shared endpoints
The same endpoints SHALL exist for every principal; authorization decides
which actions are allowed. `user` may observe everything (including evidence)
and operate runtime (stop data sources; start/stop capture, replay, and
scenario runs). `admin` can additionally edit projects/data-sources/schemas/
scenarios, import/export, manage retention, and manage access.

#### Scenario: Same endpoint, different outcome by role
- **WHEN** a `user`-role principal calls the schema update endpoint
- **THEN** the request is rejected as unauthorized, while the same call from
  an `admin`-role principal succeeds

## Known gaps
- There are no application-level `/healthz`/`/readyz` endpoints; process
  liveness/readiness is exposed only via Spring Boot Actuator
  (`/actuator/health/**`). Per-source health is exposed at
  `/api/v1/data-sources/{id}/health`.
- `GET/PATCH /projects/{id}/settings` and `GET/PATCH /environment/settings`
  do not exist yet - project and environment settings are not yet
  implemented as a capability (tracked as a pending change, not part of this
  baseline).
