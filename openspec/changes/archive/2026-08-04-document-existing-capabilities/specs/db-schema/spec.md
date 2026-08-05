## Purpose

The Postgres schema, evolved through append-only Flyway migrations, persists
the domain model and the append-optimized value timeline that recording,
replay, and evidence read from.

## ADDED Requirements

### Requirement: Migrations are append-only and collision-safe
Every schema change SHALL be a new Flyway migration file; an already-applied
migration SHALL never be edited. Because branches run in parallel, a new
migration SHALL use either the next unclaimed sequential `V<n>__` version or,
when that could collide with another open branch, a timestamped
`V<YYYYMMDD>_<HHMM>__` version — a version number is never reused.

#### Scenario: Two branches add migrations without colliding
- **WHEN** two feature branches each add a schema change around the same time
- **THEN** using timestamped versions lets both apply cleanly regardless of
  merge order

### Requirement: Core entity tables match the domain model
The schema SHALL persist `projects`, `data_sources` (including
`simulator_port`, nullable `real_device_endpoint`, `runtime_config` jsonb,
`security_config` jsonb defaulting to `{}`), `schemas`, `schema_nodes`
(including `array_dimensions` and `type_definition` for the extended OPC UA
address-space model), `schema_node_references` (typed directed references
between schema nodes), `manual_schemas` (jsonb node array, not normalized
rows), `recordings` (scoped by `protocol`, with a nullable `data_source_id`
and a `scan_type` of `SCHEMA_ONLY | SCHEMA_AND_DATA`), `samples`, `scenarios`,
`scenario_steps`, `runs`, `run_sources`, `evidence`, and
`client_connections`.

#### Scenario: Recording survives its originating data source's deletion
- **WHEN** the data source a recording was captured from is deleted
- **THEN** the recording row is retained (`data_source_id` becomes/stays
  nullable) because a recording is scoped to a protocol, not a specific
  source instance

### Requirement: Runtime and audit event streams are separate tables
`runtime_events` and `activity_events` SHALL be distinct append-only tables,
each indexed for time-ordered reads scoped to a project, and SHALL never be
combined into one table or view.

#### Scenario: A source stop produces no activity_events row
- **WHEN** the supervisor stops a data source's worker due to a runtime
  error (not a user action)
- **THEN** a `runtime_events` row is written, and no `activity_events` row is
  written for that same occurrence

### Requirement: Value timeline is append-optimized and partitioned
The `value_timeline` table SHALL capture every value change on the recording
path (no sampling), keyed by (`recording_id`, `node_id`, `source_time`,
`seq`) as its primary/clustering key, and SHALL be range-partitioned by
`source_time` (with a `DEFAULT` partition so appends always land somewhere
before a dated partition exists) using native Postgres declarative
partitioning — no external time-series extension.

#### Scenario: Ordered range read for replay
- **WHEN** replay reads a recording's values for a time window
- **THEN** the query is `where recording_id = ? and source_time between ? and
  ? order by source_time, seq` and reads sequentially within the recording's
  partitions

### Requirement: Run-scoped value timeline for runs without a Recording
`SYNTHETIC` and `SCENARIO` runs, which generate values live and are not
backed by a `Recording`, SHALL persist their values to a separate
`run_value_timeline` table (same column shape as `value_timeline`, keyed by
`run_id` instead of `recording_id`, not partitioned) so their evidence export
carries real values instead of an empty value-timeline.

#### Scenario: Synthetic run's evidence has real values
- **WHEN** a `SYNTHETIC` run (no backing `Recording`) completes and its
  evidence is exported
- **THEN** the exported value-timeline section is populated from
  `run_value_timeline`, not empty

### Requirement: Auth tables are only populated in shared mode
`users`, `roles`, `permissions`, `role_permissions`, `user_roles`, and
`edit_leases` SHALL exist in the schema for both modes but are only populated
in shared mode; local trusted mode does not require them to be populated.

#### Scenario: Local mode runs with empty auth tables
- **WHEN** the app runs in trusted local mode with no rows in `users`/`roles`
- **THEN** the app functions normally, since local mode does not consult
  these tables for authorization

## Known gaps
- The `schema_nodes.kind` check constraint
  (`schema_nodes_kind_check`, added in `V20260721_2145`) currently allows
  `FOLDER | OBJECT | VARIABLE | METHOD` only. `NodeKind.DATA_TYPE` (see the
  `protocol-model` capability) is not yet in that constraint, so a `DATA_TYPE`
  node persisted through the normalized `schemas`/`schema_nodes` tables (as
  opposed to a jsonb-backed `manual_schemas` row, which has no such
  constraint) would violate it — this needs the constraint widened before
  `DATA_TYPE` nodes can round-trip through a real (non-manual) `Schema`.
- The `faults` table created in `V2__recordings_samples_scenarios_faults.sql`
  is not written or read by any current application code (fault params are
  inline on `scenario_steps`/injected ad-hoc, per the `domain-model`
  capability) - it is dead schema, not an active part of this baseline.
