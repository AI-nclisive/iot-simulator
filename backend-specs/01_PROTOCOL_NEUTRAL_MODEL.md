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

Protocol-neutral primitive set — a **superset** a protocol worker draws from, not
an intersection every protocol must fill:

`BOOL, INT8, UINT8, INT16, UINT16, INT32, UINT32, INT64, UINT64, FLOAT32,
FLOAT64, STRING, BYTES, DATETIME, LOCALIZED_TEXT, GUID, STATUS_CODE,
QUALIFIED_NAME, NODE_ID, EXPANDED_NODE_ID, XML_ELEMENT`

This covers every concrete OPC UA built-in type usable as a Variable's `DataType`
attribute except the structural/complex ones (`Structure`/`ExtensionObject`, kept
out per the no-nested-structs rule below) and the protocol-machinery container
types (`DataValue`, `Variant`, `DiagnosticInfo`) that a real server's Variable
never literally declares as its `DataType` in practice.

A schema/recording/data-source is always scoped to a single protocol (`ReplayGuards`
rejects replaying a recording against a different protocol's data source), so a
value only one protocol produces (e.g. OPC UA's `LOCALIZED_TEXT`) is simply never
referenced by other protocols' workers — harmless dead weight, not a correctness
concern. Each protocol worker's own `XxxTypes` mapper (e.g. `OpcUaTypes`) reverse-maps
its native types onto whichever subset of this enum it needs.

A native type is promoted to a first-class entry here only when (a) it carries
information the user needs to interact with directly (e.g. `LOCALIZED_TEXT` is
user-visible display text) and (b) it's a bounded, well-known type — not a general
structure/variant. Anything else (structs, `ExtensionObject`, arrays-of-structs,
enums, etc.) stays unresolved, per the rules below.

OPC UA also defines standard *named subtypes* of these built-ins with a distinct
`DataType` NodeId but an identical wire/value encoding to their parent (e.g.
`UtcTime`/`Date` are `DateTime`; `Duration` is `Double`; `IntegerId`/`Counter` are
`UInt32`; `NumericRange`/`Time`/`LocaleId`/`NormalizedString`/`DecimalString`/
`DurationString`/`TimeString`/`DateString` are `String`). `OpcUaTypes` aliases these
fixed, spec-defined subtypes onto their parent's neutral type — they don't get
their own `DataType` entry since there's nothing distinct to represent. This is
different from vendor/custom subtypes of a built-in, which stay "unknown" (their
shape isn't spec-fixed, so aliasing them would be a guess).

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
- **ManualSchema**: authors a reusable node tree by hand (no scan), independent of
  any data-source; consumed as the structure basis for a synthetic source (see
  `03_DOMAIN_MODEL.md` §ManualSchema).
- **Synthetic**: generates neutral values from patterns + seed.
- **Scenarios**: sequence replay/synthetic/fault/timing over the neutral model.
- **Faults**: applied at the neutral layer and/or mapped to protocol-specific
  faults per worker (`ARCHITECTURE.md`).
- **Evidence**: value timelines + events exported from neutral data.

## Open questions for reviewer

- Confirm the primitive type set (esp. dropping nested structs in v1).
- Confirm default Modbus register auto-layout vs. requiring explicit bindings.
