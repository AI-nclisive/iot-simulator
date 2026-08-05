## ADDED Requirements

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
