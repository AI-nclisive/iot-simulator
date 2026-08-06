## Why

Synthetic source startup has an asynchronous worker handshake, but the UI left
the source actionable as stopped during that wait.

## What Changes

- Show and guard a pending start action until the request completes.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This is transition feedback, not a product capability change.

## Impact

Frontend store, detail control, and regression test only.
