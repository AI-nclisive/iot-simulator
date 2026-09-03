# db-schema Specification

## Purpose
The Postgres schema, evolved through append-only Flyway migrations, persists
the domain model and the append-optimized value timeline that recording,
replay, and evidence read from.
## Requirements
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

### Requirement: Schema node kind constraint set
The `schema_nodes.kind` check constraint SHALL admit exactly `FOLDER`,
`OBJECT`, `VARIABLE`, and `METHOD`. `DATA_TYPE` (a valid model-level node kind
— see the `protocol-model` capability) is deliberately not in this set yet, so
custom structured types are storable only through the jsonb-backed
`manual_schemas.nodes` document, never through normalized `schema_nodes` rows.

#### Scenario: Unsupported kind is rejected at the database boundary
- **WHEN** a row with `kind = 'DATA_TYPE'` is inserted into `schema_nodes`
- **THEN** the insert fails on the check constraint

### Requirement: Tables present but not used by application code
Some tables exist in the migrated schema that no application code reads or
writes: `faults` (fault parameters are inline on `scenario_steps` or passed
ad-hoc to the worker — see the `domain-model` capability), and
`project_settings` / `environment_settings` (no settings API exists — see the
`api-contract` capability). They SHALL be treated as inert: nothing depends on
their contents, and a change may drop or repurpose them without migrating
data out.

#### Scenario: Empty inert tables do not affect behavior
- **WHEN** the application runs with `faults`, `project_settings`, and
  `environment_settings` all empty
- **THEN** every feature behaves normally, because no code path reads them

### Requirement: Optional explicit Modbus register binding on schema_nodes
`schema_nodes` SHALL have nullable `modbus_register_kind` and
`modbus_address` columns for an explicit, user-pinned Modbus register/coil
override. The two columns SHALL be null together or set together, and
`modbus_register_kind` SHALL be constrained to `COIL`, `DISCRETE_INPUT`,
`HOLDING_REGISTER`, or `INPUT_REGISTER` when set.

#### Scenario: Binding columns default to null
- **WHEN** a schema node is saved with no explicit Modbus binding
- **THEN** both `modbus_register_kind` and `modbus_address` are null

#### Scenario: Setting only one half of the pair is rejected
- **WHEN** an insert or update sets `modbus_register_kind` without
  `modbus_address`, or vice versa
- **THEN** the write fails on the pairing check constraint

### Requirement: Schema-node Modbus encoding metadata is persisted

`schema_nodes` SHALL persist nullable `modbus_byte_order`,
`modbus_word_order`, and `modbus_scale` columns with the explicit Modbus
binding. Byte order values SHALL be constrained to `BIG_ENDIAN` or
`LITTLE_ENDIAN`; word order values SHALL be constrained to `MSW_FIRST` or
`LSW_FIRST`; a present scale SHALL be finite and non-zero at application
validation time.

#### Scenario: Existing binding remains readable

- **WHEN** a row written before the encoding metadata migration is read
- **THEN** its null metadata is interpreted as the established default layout
  and scale

