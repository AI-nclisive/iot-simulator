## Why

The synthetic-source wizard silently converts manual-schema variables that reference a native data type into `FLOAT64`. This violates the existing snapshot and native-type contracts, producing a source with a different schema from the one the user authored.

## What Changes

- Preserve a variable's native-type reference while preparing synthetic configuration instead of substituting a primitive fallback.
- Restrict native variables to an executable strategy supported by the existing synthetic model, with actionable validation when the type cannot be materialized.
- Add regression coverage for the manual schema to synthetic-source path with a structured variable.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None. This fixes an implementation that violates existing `protocol-model` and `domain-model` requirements; no product contract changes.

## Impact

- `frontend/src/surfaces/synthetic-profile-step.tsx`
- Synthetic configuration passed through the existing data-source API
- Frontend and domain regression tests
