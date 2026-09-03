## Why

IS-213 made a Modbus unit id available to the application by encoding it in a
worker-only `host:port#unitId` endpoint suffix. That leaks transport behaviour
into persisted connection URLs and means every reconnecting path must parse an
opaque string. A real-device connection needs a typed unit id that can be
validated, persisted, exported, and sent to the worker without rewriting its
endpoint.

## What Changes

- Persist the Modbus real-device unit id separately from its endpoint and
  migrate existing suffix-form endpoints safely.
- Carry the typed optional unit id through scan, test, rescan, capture, export,
  import, and worker RPCs.
- Remove suffix parsing from the Modbus worker while retaining compatibility for
  previously persisted source rows during migration.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `api-contract`: Modbus real-source URLs and unit ids are distinct API fields.
- `db-schema`: data sources persist an optional Modbus real-device unit id.
- `worker-contract`: client-mode requests carry a typed optional Modbus unit id.
- `frontend-screens`: real-source Modbus forms retain the selected unit id as a
  separate field.

## Impact

Updates the data-source model and Flyway schema, API DTOs and OpenAPI, scan and
capture ports, supervisor request mapping, Modbus worker client entry points,
project import/export, and frontend response/store models. No dependency is
added.
