## Why
`workers/worker-modbus` is a scaffold stub only (`ModbusWorkerMain` prints and
exits, `ModbusTypes` maps only single-register types). Modbus TCP is a
recognized first-class protocol throughout the domain model, API, and
frontend (hidden behind a flag pending this task), but no worker implements
the `ProtocolDataSource` contract for it, so a Modbus data source cannot be
scanned, written to, replayed, or recorded against. This closes IS-059: a real
j2mod-backed Modbus TCP worker implementing the full contract, symmetric with
`worker-opcua`.

## What Changes
- Implement `ModbusWorkerMain` / `WorkerServer` / `ModbusProtocolService`
  (the `ProtocolDataSource` gRPC service) in `workers/worker-modbus`.
- Implement `ModbusServerRuntime`: a j2mod Modbus TCP slave whose process
  image is built from the neutral schema, using the protocol-model's existing
  default contiguous register layout rule (schema order -> register address,
  no new persisted binding needed for this change).
- Extend `ModbusTypes` with the full primitive set representable over Modbus
  registers/coils, including multi-register `INT32`/`UINT32`/`FLOAT32`
  (big-endian, most-significant-register-first word order).
- Implement `Scan` as an active register probe: since Modbus has no
  browsing/metadata, the worker itself reads candidate coil/discrete-input/
  holding-register/input-register addresses across a bounded range and
  surfaces every address that answers without a Modbus exception as a
  discovered node, with a best-effort guess at 32-bit pairing flagged for user
  confirmation (mirrors the existing "unknown type needs resolution" pattern).
- Implement `Capture` as a poll loop (j2mod master) at a configurable
  interval, since Modbus has no push/subscribe mechanism; changed values are
  streamed exactly like OPC UA's push-based capture.
- Implement `ApplyValues`/`TestConnection`/`ClientEvents`/`RuntimeEvents`/
  `InjectFault`/`Health`/`Shutdown` mirroring `worker-opcua`.
- Unhide the "Modbus TCP" protocol option in the create-data-source wizard.

Out of scope (deferred to IS-060): persisting a user-overridable register
binding per node; this change only implements the already-specified default
layout.

## Capabilities

### Modified Capabilities
- `worker-contract`:
  - `Capture`'s "subscribes ... without sampling" requirement is clarified for
    protocols with no native push/subscribe mechanism (Modbus): the worker
    satisfies it via a bounded polling interval, so "no sampling" means no
    sampling below the poll interval rather than literal push delivery.
  - Adds a `Scan` discovery requirement for protocols with no native
    browsing/metadata (Modbus): the worker actively probes a bounded address
    range itself and surfaces every address that answers as a discovered
    node, instead of relying on server-advertised structure.

## Impact
- `workers/worker-modbus/src/main/java/...` (new implementation files).
- `frontend/src/surfaces/create-data-source-wizard-page.tsx` (unhide Modbus).
- No DB migration, no supervisor change, no API contract change.
