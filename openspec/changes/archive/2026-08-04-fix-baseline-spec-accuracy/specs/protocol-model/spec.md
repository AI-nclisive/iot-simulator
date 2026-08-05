## ADDED Requirements

### Requirement: DATA_TYPE node storage scope
`DATA_TYPE` nodes are supported by the model and by jsonb-backed manual
schemas, but the normalized schema storage does not accept them yet: the
`schema_nodes.kind` constraint admits only `FOLDER`, `OBJECT`, `VARIABLE`, and
`METHOD` (see the `db-schema` capability). A `DATA_TYPE` node SHALL therefore
be rejected rather than silently dropped when a caller tries to persist one
into a data source's normalized `Schema`.

#### Scenario: DATA_TYPE survives in a manual schema
- **WHEN** a manual schema containing a `DATA_TYPE` node is saved
- **THEN** it round-trips intact, because manual-schema nodes are stored as a
  single jsonb document

#### Scenario: DATA_TYPE cannot yet be stored in a normalized schema
- **WHEN** a `DATA_TYPE` node is written to a data source's normalized
  `Schema` (the `schemas`/`schema_nodes` tables)
- **THEN** the write fails on the `kind` constraint rather than persisting a
  partial or coerced node
