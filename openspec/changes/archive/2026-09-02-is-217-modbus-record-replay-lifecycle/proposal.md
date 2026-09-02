## Why

Modbus TCP capture and replay are individually covered, but the production
record-to-replay lifecycle has no deterministic integration proof. A local
simulated endpoint can exercise that boundary without depending on physical
equipment.

## What Changes

- Add a deterministic integration test that captures values from a local
  Modbus TCP slave, finalizes the recording, and replays it to a local Modbus
  TCP listener.
- Wire the test task to use the packaged Modbus worker distribution.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This change adds regression coverage for existing recording and replay
behavior without changing the product contract.

## Impact

- Affects app integration-test coverage and its Gradle test setup.
- Uses the existing Modbus worker and local loopback TCP only; no API,
  persisted-data, or dependency changes.
