# Protocol-Neutral Schema & Value Model (DRAFT)

Status: **DRAFT — proposal for approval.** This is the load-bearing abstraction
named in `ARCHITECTURE.md` ("a single protocol-neutral schema and value model is
the source of truth … each worker projects it onto its native protocol address
model"). It is one of the two approved abstractions; this doc defines it
concretely. Recording, replay, synthetic generation, scenarios, and faults
operate **only** on this model — never per-protocol.

## 1. Schema model

A **schema** is a versioned, ordered tree of **nodes** that describes the
addressable structure of one data-source, independent of protocol.

### Node

| Field | Type | Notes |
| --- | --- | --- |
| `nodeId` | stable string (ULID/slug) | Identity; stable across edits and exports. |
| `parentId` | string \| null | Tree structure; null = root child. |
| `path` | string | Human-readable address, e.g. `Plant/Line1/Temp`. Derived, unique within schema. |
| `name` | string | Display name. |
| `kind` | enum `FOLDER \| VARIABLE` | Folders group; variables carry values. |
| `dataType` | enum (see §2) | Required for `VARIABLE`. |
| `valueRank` | enum `SCALAR \| ARRAY` | Arrays carry an `arrayLength` hint. |
| `access` | enum `READ \| READ_WRITE` | Default `READ`. |
| `unit` | string \| null | Engineering unit (informational). |
| `description` | string \| null | |
| `protocolBindings` | map<protocol, binding> | Projection hints, see §5. Optional; defaults derived. |

Rules: `path` is unique within a schema; `nodeId` is the only stable reference
(used by recordings, samples, scenarios, faults, evidence). A schema is
**immutable once a recording references it**, except via a new schema version.

### Schema versioning

Schemas are versioned (monotonic int). A recording/sample stores the
`schemaVersion` it was captured against so replay can detect drift and warn (per
`frontend/docs/DESIGN.md` compatibility rules).

## 2. Data types

Protocol-neutral primitive set (chosen as the intersection that maps cleanly to
both OPC UA built-in types and Modbus registers):

`BOOL, INT16, UINT16, INT32, UINT32, INT64, UINT64, FLOAT32, FLOAT64, STRING,
BYTES, DATETIME`

- `valueRank = ARRAY` wraps any primitive into a homogeneous array.
- No nested structs in v1 (kept out to bound worker complexity; revisit with
  approval if a real source needs it). Discovered struct-like OPC UA nodes are
  flattened into folders + variables during scan.
- Unknown discovered types are surfaced as `unknown` in scan results (the UI has
  an "unknown data type" state) and require user resolution before create.

## 3. Value model

A **value** is a single observed/produced sample for one variable node.

| Field | Type | Notes |
| --- | --- | --- |
| `nodeId` | string | Target variable. |
| `sourceTime` | instant (UTC, micros) | When the value is logically valid. Drives determinism. |
| `value` | typed scalar/array | Encoded per `dataType`. |
| `quality` | enum `GOOD \| UNCERTAIN \| BAD` | |
| `qualityReason` | enum \| null | e.g. `STALE, COMM_FAILURE, OUT_OF_RANGE, FAULT_INJECTED`. |

`sourceTime` is the authoritative ordering key. A separate delivery/server time
may exist at the protocol layer but is **not** part of the neutral model and is
**not** deterministic.

### Value timeline

A **value timeline** is the append-only ordered stream of value changes for a
data-source (every change captured on the recording path — no sampling, per
`ARCHITECTURE.md`). The live path is a conflated/throttled projection of the same
stream for the UI. Storage design is in `04_DB_SCHEMA.md`.

## 4. Determinism

Guaranteed (per `ARCHITECTURE.md`): generated **value content** and **scenario
step ordering**, via an explicit injectable `Clock` and a **seeded** RNG. The
same seed + same schema + same scenario ⇒ identical value sequence and event
order. **Not** guaranteed: client delivery timing.

## 5. Protocol projection

Each worker maps the neutral schema onto its native address model. The mapping is
deterministic and, where unset, derived by default rules.

### OPC UA (Eclipse Milo)

- Folders → `FolderType` objects; variables → `BaseDataVariableType` nodes.
- `nodeId` → OPC UA `NodeId` in a product namespace (e.g.
  `ns=2;s=<path>`); namespace index allocated by the worker.
- `dataType` → OPC UA built-in type; `valueRank` → OPC UA ValueRank.
- `quality` → OPC UA `StatusCode`.

### Modbus TCP (j2mod)

- Variables bind to a register map via `protocolBindings.modbus`:
  `table` (`COIL \| DISCRETE_INPUT \| HOLDING \| INPUT`), `address`,
  `wordCount`, `wordOrder` (`BIG_ENDIAN \| LITTLE_ENDIAN`), optional `scale`/
  `offset`.
- `BOOL` → coil/discrete; integers/floats → holding/input registers spanning
  `wordCount` 16-bit words.
- `quality`/`BAD` → exception response or held last-good per fault config.

Default binding allocation (when not provided): contiguous register layout
assigned in schema order; surfaced in scan/review so the user can adjust.

## 6. Where this model is used

- **Recording**: captures neutral values into a timeline.
- **Replay**: streams a timeline back through a worker (which re-projects).
- **Synthetic**: generates neutral values from patterns + seed.
- **Scenarios**: sequence replay/synthetic/fault/timing over the neutral model.
- **Faults**: applied at the neutral layer and/or mapped to protocol-specific
  faults per worker (`ARCHITECTURE.md`).
- **Evidence**: value timelines + events exported from neutral data.

## Open questions for reviewer

- Confirm the primitive type set (esp. dropping nested structs in v1).
- Confirm default Modbus register auto-layout vs. requiring explicit bindings.
