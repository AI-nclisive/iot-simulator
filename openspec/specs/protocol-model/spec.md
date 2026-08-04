# protocol-model Specification

## Purpose
The protocol-neutral schema and value model is the single source of truth that
recording, replay, synthetic generation, scenarios, and faults all operate on;
each protocol worker only projects it onto its own native address model.
## Requirements
### Requirement: Schema node model
A schema SHALL be a versioned, ordered tree of nodes, each with a stable
`nodeId`, optional `parentId`, a `path` unique within the schema, a `kind`
(`FOLDER | OBJECT | VARIABLE | METHOD | DATA_TYPE`), and — for `VARIABLE` — a
`dataType` and `valueRank` (`SCALAR | ARRAY`). `DATA_TYPE` nodes are top-level
(never nested under a parent) and instead carry named, typed `members`
(one level of nesting; a member may reference another `DATA_TYPE` node via
`dataTypeNodeId`, never itself).

#### Scenario: Path uniqueness
- **WHEN** a schema is edited so two nodes would share the same `path`
- **THEN** the save is rejected

#### Scenario: Immutability once referenced
- **WHEN** a recording already references a schema version
- **THEN** further edits to that data source's schema produce a new schema
  version rather than mutating the referenced one

#### Scenario: Custom struct type reference
- **WHEN** a `VARIABLE` node sets `dataTypeNodeId`
- **THEN** the target node SHALL be an existing `DATA_TYPE` node, and the
  variable's value SHALL be shaped by that node's `members`

### Requirement: Typed node references
The schema model SHALL support typed, directed references between nodes
(`ORGANIZES`, `HAS_COMPONENT`, `HAS_PROPERTY`, `HAS_TYPE_DEFINITION`,
`GENERIC`), in addition to the plain parent/child tree, to represent an OPC UA
address space faithfully (e.g. a Variable's `HasTypeDefinition` link to an
Object/VariableType node).

#### Scenario: Type-definition link survives export/import
- **WHEN** a schema with `HAS_TYPE_DEFINITION` references is exported and
  re-imported
- **THEN** the references resolve to the same (re-mapped) node ids

### Requirement: Data type set
The neutral primitive `dataType` set SHALL be a superset every protocol worker
draws a subset from, not an intersection every protocol must fill:
`BOOL, INT8, UINT8, INT16, UINT16, INT32, UINT32, INT64, UINT64, FLOAT32,
FLOAT64, STRING, BYTES, DATETIME, LOCALIZED_TEXT, GUID, STATUS_CODE,
QUALIFIED_NAME, NODE_ID, EXPANDED_NODE_ID, XML_ELEMENT`. No nested structs
outside the `DATA_TYPE` node mechanism above. A discovered type this set
cannot represent SHALL be surfaced as `unknown`, never silently coerced.

#### Scenario: Struct-like OPC UA node flattened on scan
- **WHEN** a scan discovers a struct-like node this set cannot model directly
  as a single `VARIABLE`
- **THEN** it is flattened into folders + variables during scan

#### Scenario: Unknown type blocks create
- **WHEN** a scanned node's native type has no mapping in the neutral set
- **THEN** the node is surfaced as `unknown` and requires user resolution
  before the data source can be created from that scan

### Requirement: Value model and ordering
A value SHALL carry `nodeId`, `sourceTime` (UTC, microsecond precision),
`value`, `quality` (`GOOD | UNCERTAIN | BAD`), and an optional `qualityReason`.
`sourceTime` SHALL be the authoritative ordering key for replay and evidence;
any protocol-layer delivery/server time is not part of this model and is not
deterministic.

#### Scenario: Ordering by source time, not arrival
- **WHEN** two values for the same node are captured
- **THEN** replay and evidence order them by `sourceTime` (then `seq` as a
  tiebreaker), never by wall-clock arrival order

### Requirement: Determinism
Generated value content and scenario step ordering SHALL be deterministic
given an explicit injectable clock and a seeded RNG: the same seed + same
schema + same scenario produces an identical value sequence and event order.
Client delivery timing is explicitly not covered by this guarantee.

#### Scenario: Same seed reproduces the same series
- **WHEN** a synthetic run is started twice with the same seed, schema, and
  scenario
- **THEN** the two runs produce identical value sequences and step ordering

### Requirement: Protocol projection
Each worker SHALL map the neutral schema onto its native address model
deterministically, using default rules where no explicit binding is set.

#### Scenario: OPC UA projection
- **WHEN** an OPC UA worker is configured with a neutral schema
- **THEN** folders become `FolderType` objects, variables become
  `BaseDataVariableType` nodes, and each `nodeId` maps to an OPC UA `NodeId`
  in a worker-allocated namespace

#### Scenario: Modbus default register layout
- **WHEN** a Modbus TCP schema variable has no explicit `protocolBindings.modbus`
- **THEN** the worker assigns a contiguous register address in schema order
  and surfaces the assignment for user review

