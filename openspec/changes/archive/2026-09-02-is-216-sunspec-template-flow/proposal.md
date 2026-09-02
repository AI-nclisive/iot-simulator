## Why

IS-214 added the SunSpec inverter profile to the domain catalog, but no
production API or screen can instantiate it. Customers therefore still have to
hand-enter the register map and cannot use the shipped profile.

## What Changes

- Add a server-side manual-schema creation operation for a named built-in
  profile. It materializes the catalog's explicit Modbus bindings unchanged.
- Offer SunSpec Inverter as a starting point when creating a Modbus TCP manual
  schema in the Manual Schemas screen.
- Preserve Modbus binding fields in the frontend schema DTO.

## Capabilities

### Modified Capabilities

- `api-contract`: manual-schema creation gains an additive built-in-profile
  endpoint.
- `frontend-screens`: the Manual Schemas creation dialog presents the available
  Modbus starting profile.

## Impact

- `domain`, `api`, `app`, and `frontend` modules.
- No migration or dependency change.
