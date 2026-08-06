## Why

When a real-device capture gRPC stream terminates unexpectedly, the supervisor
currently leaves the recording service believing that capture is still active.
The UI then reports `capturing=true` although no worker remains and no further
values can arrive.

## What Changes

- Propagate unexpected terminal capture-stream failures from the runtime
  supervisor to the recording owner after the worker session is torn down.
- Clear the matching active recording and finalize values received before the
  failure.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `worker-contract`: An unexpectedly terminated Capture stream stops the
  owning recording rather than leaving it active.

## Impact

Updates the capture port, runtime supervisor, recording service, and their
unit/integration-level tests. No persistence migration or public API shape
change is required.
