## Why

An open-ended recording can only finish through the user's Stop action, yet
its evidence was presented as incomplete. Users need intentional completion to
be distinguishable from missing data and failures.

## What Changes

- Add a distinct `STOPPED` completeness outcome for deliberately stopped runs.
- Keep incomplete evidence as `PARTIAL` and failed evidence as `FAILED`.
- Keep successfully exported stopped evidence available as `READY`.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `artifact-formats`: evidence completeness distinguishes an intentional stop.

## Impact

Evidence manifests and UI labels distinguish stopped runs; existing API status
values remain compatible.
