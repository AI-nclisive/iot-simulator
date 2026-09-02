## Why

Modbus TCP gateways can expose multiple real devices at one endpoint. The
create-source wizard currently cannot target a non-default unit while scanning
or testing a real device, even though the API now supports it.

## What Changes

- Add a Modbus-only real-device unit ID field to the scan setup step, defaulting
  to `1` and validating the API-supported `0` through `255` range.
- Include the selected unit ID in Modbus test-connection, scan, and
  scan-created-source requests, while preserving the existing payloads for OPC
  UA.
- Treat a unit-ID edit as a changed scan target so stale scan results are not
  reused.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `frontend-screens`: The create-data-source guided flow targets a specific
  Modbus TCP real-device unit during scan and capture setup.

## Impact

- `frontend/src/surfaces/create-data-source-wizard-page.tsx`
- `frontend/src/surfaces/create-data-source-wizard-page.test.tsx`
- The scan and scan-created-source REST request payloads supplied by IS-213
