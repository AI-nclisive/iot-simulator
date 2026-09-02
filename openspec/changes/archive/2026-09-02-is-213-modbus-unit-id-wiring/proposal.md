## Why

Modbus TCP devices behind gateways and multi-drop networks commonly use a unit id other than
the default `1`. The Modbus worker can use such an id internally, but the public API cannot
carry it through connection testing, discovery, and subsequent capture.

## What Changes

- Add an optional `unitId` to real-source Modbus TCP scan and test-connection requests.
- Persist the selected unit id with a scanned Modbus source so rescan and recording capture use
  the same device.
- Enable the runtime supervisor to dispatch Modbus TCP client-mode test, scan, and capture calls.
- Keep omitted unit ids compatible with the default Modbus unit id of `1`; leave OPC UA requests
  unchanged.

## Capabilities

### New Capabilities

- None.

### Modified Capabilities

- `api-contract`: Real-source Modbus TCP operations accept and retain an optional Modbus unit id.

## Impact

- API scan request and create-from-scan request DTOs, OpenAPI output, and API tests.
- Scan/capture application ports and runtime-supervisor request construction.
- No new dependency, migration, or worker protobuf field.
