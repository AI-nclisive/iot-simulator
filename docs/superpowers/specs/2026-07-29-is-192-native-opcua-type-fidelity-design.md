# IS-192 — Native OPC UA Type Fidelity

## Decision

Schemas retain the exact OPC UA type declaration and, whenever the endpoint
provides it, the transitive type definition required to decode, encode, and
materialize the value. The existing neutral scalar enum remains the compact
representation for scalar values; it is no longer the ceiling of the schema.

`DATA_TYPE` is retained as the schema representation introduced by IS-183, but
the persisted schema gains a type catalog. A variable references either a
neutral scalar or a catalog entry. `Range` is represented as a catalog
`STRUCTURE` with `low` and `high` `FLOAT64` fields; a node named `EURange` keeps
that browse name while its declared type remains `Range`.

## Compatibility

- Existing scalar nodes are unchanged.
- Existing `dataTypeNodeId` values without a catalog definition become an
  `OPAQUE` catalog entry at read/migration time; their declaration is never
  replaced.
- Existing `DATA_TYPE` nodes migrate into catalog `STRUCTURE` definitions.
- A recording carries a snapshot of both schema nodes and catalog definitions.
  Old recordings remain readable as scalar/opaque snapshots.

## Catalog model

`NativeTypeDefinition` contains:

- schema-local `typeId`, namespace URI, native NodeId, browse/display names;
- `kind`: `ENUM`, `STRUCTURE`, `UNION`, `OPTION_SET`, or `OPAQUE`;
- optional base type and binary/XML encoding NodeId;
- ordered fields: name, scalar or referenced type, value rank/dimensions, and
  optional/union-switch metadata;
- enum values and option-set bit labels;
- raw definition metadata for exact export/import and diagnostics;
- capability: `MATERIALIZABLE`, `CAPTURE_DECODABLE`, `REPLAY_ENCODABLE`, or an
  explicit reason why one is unavailable.

Recursive definitions are represented by references and validated as a graph.
Cycles permitted by OPC UA are retained in the catalog but cannot be used for a
finite generated value unless the worker supplies a codec.

## Scan, capture, and replay

1. Scan reads a variable's DataType NodeId, namespace URI, DataTypeDefinition,
   enum/structure metadata, and encoding references. It imports the transitive
   closure into the scan result.
2. Persisting a scan writes nodes plus type definitions atomically into a new
   schema version. No type-resolution control is shown for a definition that
   was successfully imported.
3. Capture stores a canonical tagged tree/array value encoding for catalog
   types. It preserves source DataValue status/timestamps as before.
4. Replay uses the same catalog snapshot and asks the OPC UA worker to encode
   the original shape. If a codec is unavailable, start/capture/replay fails at
   the affected type with a named capability error.
5. The worker materializes standard namespace-zero structures first, then
   imported enums and structures with an available encoding. No value is
   converted to text, bytes, or a default primitive merely to start a source.

## API and IPC

- Schema responses and scan responses carry `typeDefinitions` beside `nodes`.
- Schema save/create/import requests accept the same additive field.
- Recording snapshots and project export/import include the catalog.
- `SchemaNodeMsg`/`Schema` gain additive type-definition messages; the worker
  contract receives a minor version bump and refuses a missing required catalog
  only when a node references it.

## UI

- Scan summary groups imported types by name/kind and only labels an `OPAQUE`
  declaration as limited; it never asks the user to guess a primitive.
- The schema details panel shows a variable's declared type and a linked type
  inspector with fields, enum values, dimensions, encoding, and capabilities.
- Manual schema editing adds a type catalog editor. Users can create primitive
  aliases, enums, and structures and select them for variables or structure
  fields. Opaque imported types are read-only except for a description.
- Synthetic generation exposes only strategies supported by the selected type;
  a structure can use a typed constant once its codec is materializable.

## Verification

- Unit tests cover `Range`, nested custom structures, enums, arrays of
  structures, opaque definitions, catalog validation, persistence round trips,
  and value encoding.
- Worker integration tests scan and replay a Milo server exposing `Range` and a
  custom structure without changing either declared DataType.
- API tests verify catalog/recording/export round trips.
- Frontend tests verify the scan summary and type inspector/catalog behavior.
