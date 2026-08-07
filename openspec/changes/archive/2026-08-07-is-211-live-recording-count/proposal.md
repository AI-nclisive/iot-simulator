## Why

During a real-device capture, the recording page displayed a value count based
on conflated SSE rows rather than the full-fidelity persisted value timeline.
It could therefore show zero or a much smaller count until the recording was
stopped.

## What Changes

- Return live value count and byte size from recording metadata while capture
  is active.
- Have the recording flow refresh that authoritative metadata rather than infer
  a recording count from live SSE rows.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `api-contract`: Active recording metadata exposes the values persisted so far.

## Impact

Updates recording metadata mapping, recording-flow observation, and tests. No
database migration or endpoint shape change is required.
