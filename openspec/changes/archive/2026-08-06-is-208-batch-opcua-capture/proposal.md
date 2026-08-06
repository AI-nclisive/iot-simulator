## Why

The clean-master E2E check against the public OPC UA demo endpoint discovers
and saves 883 variables, but recording receives no values and disconnects. The
worker creates every monitored item in one remote request, exceeding the
practical capacity of this real endpoint before it can emit the initial
snapshot.

## What Changes

- Create OPC UA monitored items in bounded batches for large scanned schemas.
- Emit each successfully subscribed batch's initial readable values before
  proceeding to later batches.
- Keep a failed batch from suppressing capture for successful batches.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. The existing worker-contract requirement already requires readable
variables to continue capture when another variable cannot be captured; this is
an implementation correction for large schemas.

## Impact

Updates the OPC UA worker capture lifecycle and its integration tests. No
public API, dependency, or persistence-schema change is required.
