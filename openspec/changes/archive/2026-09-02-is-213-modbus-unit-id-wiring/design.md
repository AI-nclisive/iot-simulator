## Context

The worker contract has only `endpoint_url` for client-mode test, scan, and capture. The Modbus
worker already interprets a trailing `#<unitId>` as its target unit, but the API cannot create or
persist that representation and the supervisor rejects Modbus client-mode operations before they
reach the worker.

## Goals / Non-Goals

**Goals:**

- Make one optional API field reach the existing Modbus client implementation consistently.
- Preserve the target unit across create-from-scan, rescan, and capture without a schema migration.
- Keep the existing worker protobuf API stable.

**Non-Goals:**

- Do not expose a Modbus unit id for simulated-server configuration.
- Do not introduce a separate worker-contract field, register-map behavior, or protocol support beyond
  the existing Modbus worker.

## Decisions

### Preserve the existing endpoint suffix at the worker boundary

The application layer will validate and encode a Modbus TCP unit id as the worker's existing
`endpoint#unitId` form. It will preserve that encoded endpoint when materializing a scanned source,
so later rescan and capture need no new persistence column or API body for recording start.

Adding a protobuf `unit_id` field was considered. It would be a cleaner worker contract but expands
the generated contract and requires coordinated changes to every worker. The existing suffix is
already the Modbus worker's supported compatibility seam, so retaining it is the smallest additive
change.

### Validate unit id at the API/domain boundary

Only `MODBUS_TCP` accepts `unitId`, which must be an integer from `0` through `255`; absent means
`1`. The domain-facing scan specification carries the resolved endpoint, keeping the runtime
supervisor protocol-neutral except for accepting both existing client-mode protocols.

Accepting arbitrary suffix text was rejected because invalid configuration would otherwise fail only
inside a worker and could be persisted in a source endpoint.

### Enable Modbus client-mode dispatch explicitly

The supervisor's allow-list will accept `MODBUS_TCP` for test, scan, and capture alongside
`OPC_UA`. The worker launcher remains protocol-selected as before; no supervisor-specific Modbus
logic is added beyond the protocol support check.

## Risks / Trade-offs

- **[Risk]** The persisted endpoint contains worker-oriented suffix syntax. **Mitigation:** it is an
  internal opaque real-device endpoint representation and is created only from the validated API
  field; its public API requests continue to expose `unitId` separately at scan time.
- **[Risk]** Existing manually persisted endpoints with a suffix can still be used. **Mitigation:**
  preserve the worker's backward-compatible parser rather than rejecting older saved sources.

## Migration Plan

No database migration is required. Existing endpoints omit a suffix and therefore retain unit id
`1`. Rollback is a code revert; persisted suffixes remain safely readable by the existing worker.
