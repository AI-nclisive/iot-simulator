## Why

The built-in Modbus catalog currently contains only a SunSpec inverter. Users
also need a small, predictable starting point for a generic energy meter and
PLC digital I/O without manually recreating address bindings.

## What Changes

- Add generic energy-meter and PLC I/O profiles to the server-authoritative
  Modbus template catalog.
- Materialize any catalog profile through the existing manual-schema endpoint.
- Offer every supported profile in the Manual Schemas creation dialog.

## Impact

- `domain` catalog and tests.
- Existing manual-schema API and frontend creation dialog.
