## ADDED Requirements

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
