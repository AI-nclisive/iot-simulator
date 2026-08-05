## ADDED Requirements

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
