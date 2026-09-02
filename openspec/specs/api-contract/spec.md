# api-contract Specification

## Purpose
The REST/OpenAPI surface (plus SSE for live updates) is the one integration
point automation and the frontend use to command and observe the simulator;
the API layer is where authorization is enforced.
## Requirements
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
discoverable rather than only surfacing as a rejected start. While capture is
active, that recording's metadata SHALL report the full-fidelity value count
and byte size persisted so far, rather than a count inferred from conflated
live-update events.

#### Scenario: Orphaned capture is discoverable
- **WHEN** a data source's recording start returns "already capturing" but no
  UI session initiated it
- **THEN** the recording-status endpoint reports `{capturing: true,
  recordingId}` so the stuck capture can be found and stopped

#### Scenario: Active recording reports persisted progress
- **WHEN** a real-device capture has persisted values and has not yet stopped
- **THEN** retrieving its recording metadata reports the count and byte size of
  the values persisted so far

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

### Requirement: Application liveness and readiness come from Actuator
Process-level liveness/readiness SHALL be exposed via Spring Boot Actuator
(`/actuator/health/**`), reachable without authentication in both deployment
modes. There are deliberately no bespoke `/healthz` / `/readyz` endpoints.
Per-data-source health is a separate, authenticated API concern
(`/api/v1/data-sources/{id}/health`) and SHALL NOT be conflated with process
health.

#### Scenario: Orchestrator probes Actuator, not a bespoke path
- **WHEN** a container orchestrator probes the application for liveness
- **THEN** it uses `/actuator/health` and succeeds without a bearer token

#### Scenario: Per-source health is not process health
- **WHEN** one data source reports an `ERROR` runtime state
- **THEN** `/actuator/health` still reports the application UP, because a
  single source's health is not the process's health

### Requirement: No settings API surface yet
Project-scoped and environment-scoped settings are NOT exposed over the API:
there are no `/projects/{id}/settings` or `/environment/settings` endpoints,
even though backing tables exist (see the `db-schema` capability).
Environment-level configuration SHALL continue to come from environment
variables / external config (see the `auth-modes` capability) until a change
introduces that surface.

#### Scenario: Settings are configured out-of-band
- **WHEN** an operator needs to change environment-level configuration
- **THEN** they change the deployment's environment/external config, not an
  API call

### Requirement: A completed scan materializes a data source only with every unknown type resolved
A completed scan job SHALL be turned into a `SCAN`-basis data source by a
dedicated create call carrying the source name, its real-device endpoint, and a
type resolution for every unknown-typed discovered node (assign a concrete type
or exclude the node). An unresolved unknown-typed node SHALL reject the request
with `400` rather than persisting a coerced or partial schema. Success SHALL
return `201` with a `Location` header and the new source's `ETag`.

#### Scenario: Unresolved unknown type blocks creation
- **WHEN** a create-from-scan call omits a resolution for a discovered node
  whose native type has no neutral mapping
- **THEN** the request fails with `400` and no data source is created

#### Scenario: Resolved scan creates the source
- **WHEN** every unknown-typed node carries a resolution (typed or excluded)
- **THEN** the data source is created and the response is `201` with a
  `Location` header pointing at it

### Requirement: A scanned source can be rescanned without re-entering connection details
A `SCAN`-basis data source SHALL be rescannable using its already-stored
protocol, endpoint, and connection credentials — the caller supplies nothing.
Rescan SHALL start an asynchronous job, return `202` with a `Location` header,
and be pollable through the same scan-job endpoint as create-from-scan. Applying
a completed rescan SHALL save the discovered structure as a **new schema
version** on the existing source (never mutating the current one) under the same
unknown-type resolution rules as creation.

#### Scenario: Rescan needs no connection input
- **WHEN** a rescan is started on a `SCAN`-basis source
- **THEN** it connects using the source's stored endpoint and credentials, and
  returns `202` with a job id to poll

#### Scenario: Applying a rescan versions the schema
- **WHEN** a completed rescan job is applied to its source
- **THEN** the discovered structure becomes a new schema version and the
  previous version remains intact

### Requirement: Stored connection credentials can be cleared without deleting the source
A data source's stored real-device connection credentials SHALL be clearable on
their own, leaving the source and its schema in place. The response SHALL report
the source's updated credential state, never the credential value.

#### Scenario: Clearing credentials keeps the source
- **WHEN** a data source's credentials are cleared
- **THEN** the source still exists with its schema, and its response reports no
  stored credential

### Requirement: Synthetic data sources are created from a synthetic profile
Creating a synthetic data source SHALL accept a synthetic value profile plus the
structure basis (a `manualSchemaId` or a `schemaFromSourceId`, mutually
exclusive) and SHALL return `201` with a `Location` header and the new source's
`ETag`. The structure is copied by snapshot at creation time (see the
`domain-model` capability).

#### Scenario: Synthetic source is created with a snapshot structure
- **WHEN** a synthetic source is created from a manual schema
- **THEN** the response is `201` with a `Location` header, and the new source
  owns its own copy of the structure

### Requirement: A live synthetic run is real-time paced and stops like any other run
Starting a live synthetic run on a data source SHALL return immediately with the
created run, then feed generated values at wall-clock pace until an explicit
`stop` on that run. An optional maximum-duration parameter SHALL act as a safety
cap, not as the normal way to end a run. This live run is distinct from the
bounded one-shot synthetic batch used as a scenario step primitive.

#### Scenario: Live synthetic run stays RUNNING until stopped
- **WHEN** a live synthetic run is started without a duration cap
- **THEN** it stays `RUNNING` and keeps producing values until the run's stop
  endpoint is called

#### Scenario: Duration cap ends the run on its own
- **WHEN** a live synthetic run is started with a maximum duration
- **THEN** it ends by itself once that duration elapses, even with no stop call

### Requirement: A recording can seed a synthetic profile without being modified
A recording SHALL be analysable into a per-measurement statistical profile that
suggests parameters for every synthetic pattern type plus one recommended
default, so a client can prefill synthetic authoring from real captured
behavior. The call SHALL be a preview: it returns the profile and SHALL NOT
mutate the recording or create a data source.

#### Scenario: Deriving a profile leaves the recording untouched
- **WHEN** a synthetic profile is derived from a recording
- **THEN** the response carries per-measurement suggestions and a recommended
  pattern, and the recording is unchanged

### Requirement: A recording's captured schema and values are browsable
A recording SHALL expose the schema nodes it was captured against, and its
captured values SHALL be browsable with cursor pagination (a bounded default
page size and an enforced maximum) plus optional filters on node path/id,
quality, and a time window. Paging SHALL be driven by an opaque cursor returned
with each page.

#### Scenario: Values page is bounded
- **WHEN** a client requests recording values with a limit above the enforced
  maximum
- **THEN** the response is capped at the maximum rather than returning the whole
  timeline

#### Scenario: Filtered browse stays filtered across pages
- **WHEN** a client browses recording values filtered by quality and follows the
  returned cursor
- **THEN** the next page keeps the same filter

### Requirement: Recording and sample artifacts share one export, download, import shape
Recordings and samples SHALL each expose the same three-step artifact flow:
`export` rebuilds the ZIP from live data, stores it, and streams it; `download`
streams the previously stored blob and SHALL return `404` before the first
export; `import` accepts a multipart ZIP upload. Export/import are
administrative (they move data across environments); download is an observation.

#### Scenario: Download before export
- **WHEN** a client downloads a recording or sample artifact that has never been
  exported
- **THEN** the response is `404`, not an empty or partial archive

#### Scenario: Export refreshes the stored blob
- **WHEN** a recording is exported twice with new values captured in between
- **THEN** the second export rebuilds the archive from the current data rather
  than re-serving the first one

### Requirement: Samples are a first-class project resource
A `Sample` SHALL be listable (cursor-paginated), creatable, readable, and
deletable within its project, independently of the recording it was derived
from. Reading a sample requires observation rights; creating or deleting one is
an edit of recorded data.

#### Scenario: Sample outlives browsing of its recording
- **WHEN** a sample derived from a recording is fetched by id
- **THEN** it resolves on its own endpoint without going through the recording

### Requirement: Point-in-time observability queries tolerate an unknown source id
The connected-clients query (current connections plus full connection history)
and the per-source health query (current runtime state plus the most recent
error, retained after recovery) SHALL each answer for a data-source id that the
runtime does not know about with `200` and an empty/stopped result rather than
`404`. These are the point-in-time counterparts of the SSE streams, and an
unambiguous "nothing running" answer is more useful to a dashboard than an
error.

#### Scenario: Unknown source id is not an error
- **WHEN** the clients or health endpoint is called with an id the runtime has
  never started
- **THEN** the response is `200` with no clients / a stopped state and no error

#### Scenario: Last error is retained after recovery
- **WHEN** a data source failed and then recovered
- **THEN** its health response still reports the most recent error alongside the
  now-healthy state

### Requirement: Dashboard reads are aggregated, derived, and concurrency-free
Two read-only aggregations SHALL back the dashboard surfaces: the currently
active (`RUNNING` or `QUEUED`) runs for a project, each with its process type,
run state, start time, initiator, and a link back to the related source; and a
per-project overview of configured/running source counts, reusable-artifact
count, and a count of sources needing attention. Both are derived views: they
SHALL NOT carry an `ETag` or participate in optimistic concurrency.

#### Scenario: Active runs list is empty, not absent, when nothing runs
- **WHEN** a project has no `RUNNING` or `QUEUED` run
- **THEN** the active-runs response is an empty collection with `200`

#### Scenario: Overview counts unhealthy sources
- **WHEN** a project has a source in an error state
- **THEN** that source is counted in the overview's needing-attention count

### Requirement: Admin-only user management and cross-project activity
Shared mode SHALL expose admin-only endpoints to list registered users with
their current role and status, change a user's roles, and change a user's
status; plus a cross-project activity feed carrying the same filters and cursor
paging as the project-scoped one. All of these require the admin permission; in
local trusted mode the implicit principal holds it.

#### Scenario: Non-admin cannot list users
- **WHEN** a `user`-role principal calls the admin user list
- **THEN** the request is rejected as unauthorized

#### Scenario: Admin activity spans projects
- **WHEN** an admin lists activity through the admin endpoint
- **THEN** events from every project are returned, unlike the project-scoped
  endpoint

### Requirement: OPC UA NodeSet import reports what it could not represent
Importing an OPC UA NodeSet XML document SHALL create a reusable manual schema
from the definitions it supports (objects, variables, methods, and references)
and SHALL return explicit diagnostics for every definition it could not
represent. Unsupported definitions SHALL NOT be silently flattened or dropped.

#### Scenario: Unsupported definitions surface as diagnostics
- **WHEN** a NodeSet containing definitions outside the supported set is imported
- **THEN** the response is `201` with the created manual schema **and** a
  diagnostic naming each unsupported definition

### Requirement: API metadata confirms the versioned surface
The API SHALL expose a metadata endpoint reporting the application name and the
API major version, so a client can confirm which version it is talking to before
issuing resource calls. It is a normal `/api/v1` endpoint, not a public probe: in
shared mode it requires authentication like any other.

#### Scenario: Client checks the API version
- **WHEN** an authenticated client requests API metadata
- **THEN** the response reports the application name and the `v1` API version

### Requirement: The unauthenticated allowlist is limited to probes and API docs
In shared mode, only operational probes and API documentation SHALL be reachable
without authentication: Actuator health and info, and the OpenAPI document plus
Swagger UI. Every other endpoint — including API metadata and every `/api/v1`
resource — SHALL require a valid bearer token. In local trusted mode
authentication is off entirely, so this allowlist has no effect there.

#### Scenario: Swagger UI loads without a token
- **WHEN** an operator opens the Swagger UI or fetches the OpenAPI document in
  shared mode with no bearer token
- **THEN** the request succeeds

#### Scenario: A resource endpoint is not on the allowlist
- **WHEN** an unauthenticated client calls any `/api/v1` resource endpoint in
  shared mode
- **THEN** the request is rejected

### Requirement: Scenarios are an authorable resource that validates before it runs
Scenarios SHALL be listable, creatable, readable, patchable, deletable, and
duplicable within a project, with a step list whose order is the list order.
A scenario SHALL be validatable **without running it**, returning a status plus
every validation issue found, so an invalid scenario is diagnosable before it
touches a data source. Reading and validating require observation rights;
authoring requires scenario-edit rights; running is a runtime-operate action.

#### Scenario: Validation reports issues without side effects
- **WHEN** a scenario with a step referencing a missing data source is validated
- **THEN** the response lists that issue, and no run is started and no source is
  touched

#### Scenario: Step order follows list order
- **WHEN** a scenario is saved with its steps in a given order
- **THEN** reading it back returns the steps in that same order

### Requirement: A scenario run starts asynchronously and streams step progress
Starting a scenario run SHALL return immediately with the run id and the
evidence id created for it, while the steps execute in the background — the call
SHALL NOT block until the scenario finishes. Progress SHALL be observable two
ways: polling the run, and a Server-Sent Events stream per scenario run carrying
step-started, step-completed, and run-finished events, resumable with
`Last-Event-ID`.

#### Scenario: Run call does not block on execution
- **WHEN** a scenario with long-running steps is started
- **THEN** the response returns right away with a run id and evidence id, and
  the steps continue in the background

#### Scenario: Step events arrive on the stream
- **WHEN** a client subscribes to a scenario run's event stream while it executes
- **THEN** it receives a step-started and step-completed event per step, and a
  run-finished event at the end

### Requirement: A data source's schema is read and replaced as a whole
A data source's protocol-neutral schema SHALL be readable as its current version
and replaceable in one call carrying the complete node set — a full-editor save,
not a partial patch. A save SHALL create a **new schema version** and return the
new version as the `ETag`. Reading requires observation rights; saving requires
schema-edit rights.

#### Scenario: Saving a schema versions it
- **WHEN** a data source's schema is replaced with an edited node set
- **THEN** a new schema version is created, the response `ETag` reflects it, and
  the previous version remains intact

#### Scenario: A save must carry the whole node set
- **WHEN** a schema save omits the node list
- **THEN** the request is rejected rather than interpreted as "delete all nodes"

### Requirement: Recordings are a browsable, deletable project resource
Recordings SHALL be listable with cursor pagination, readable by id, creatable
directly (a recording shell, independent of a live capture), and deletable to
support retention and cleanup. Listing and reading require observation rights;
creating and deleting are edits of recorded data.

#### Scenario: A recording is deletable for cleanup
- **WHEN** an operator with edit rights deletes an old recording
- **THEN** it is removed and no longer appears in the recordings list

#### Scenario: Listing pages through recordings
- **WHEN** a project holds more recordings than one page
- **THEN** the list returns a page plus a cursor that fetches the next one

### Requirement: Runs are one unified, pollable resource across every run kind
Every run — replay, synthetic, scenario, recording — SHALL be listable with
cursor pagination and readable by id through **one** runs resource, regardless of
which endpoint started it. A run SHALL additionally expose a lightweight state
read for polling and a stop action. Listing/reading requires observation rights;
starting and stopping are runtime-operate actions.

#### Scenario: A scenario run and a replay run appear in the same list
- **WHEN** a project has both a scenario run and a replay run
- **THEN** both appear in the same runs list, distinguished by their kind rather
  than living on separate resources

#### Scenario: Polling uses the lightweight state read
- **WHEN** automation waits for a run to finish
- **THEN** it polls the run's state endpoint rather than refetching the full run
  representation

### Requirement: Real-source Modbus TCP operations retain an explicit unit id
The real-source connection-test and scan requests for a `MODBUS_TCP` data source SHALL accept an
optional `unitId` in the inclusive Modbus range `0` through `255`. When omitted, the system SHALL
use unit id `1`. Creating a data source from that scan SHALL retain the selected unit id with its
real-device connection details, so its later rescan and recording capture target the same unit.
`OPC_UA` requests SHALL not acquire a Modbus unit-id requirement or behavior.

#### Scenario: Non-default Modbus unit is scanned and recorded
- **WHEN** a client tests and scans a `MODBUS_TCP` endpoint with `unitId` set to `7`, then creates
  a data source from that scan
- **THEN** the connection test, scan, subsequent rescan, and recording capture all target Modbus
  unit `7`

#### Scenario: Omitted Modbus unit remains compatible
- **WHEN** a client tests or scans a `MODBUS_TCP` endpoint without `unitId`
- **THEN** the operation targets Modbus unit `1`

#### Scenario: OPC UA remains unit-id independent
- **WHEN** a client tests or scans an `OPC_UA` endpoint
- **THEN** its connection and capture behavior is unchanged by the Modbus unit-id capability

