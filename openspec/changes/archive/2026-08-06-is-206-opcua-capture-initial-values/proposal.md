## Why

Recording a real OPC UA source can remain empty when its values do not change
after capture starts. The worker currently relies solely on data-change
notifications, so a recording has no initial device state to persist.

## What Changes

- Capture a readable initial value snapshot for the configured OPC UA schema.
- Continue streaming subsequent data-change notifications throughout capture.
- Keep an unreadable or unsupported node from preventing values from other
  configured nodes from being captured.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `worker-contract`: Recording-in capture delivers the initial readable state
  as well as subsequent value changes.

## Impact

Updates the OPC UA worker capture implementation and its integration tests. No
public API, dependency, or persistence-schema change is required.
