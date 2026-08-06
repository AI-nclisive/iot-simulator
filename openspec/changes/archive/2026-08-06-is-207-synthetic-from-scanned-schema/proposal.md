## Why

Creating a synthetic source from a scanned OPC UA schema fails when a variable
keeps a native type binding but its selected synthetic type is executable.

## What Changes

- Materialize the selected executable type for synthetic variables copied from
  a scanned schema while preserving their descriptive source type metadata.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `domain-model`: Synthetic sources can be created from compatible scanned schemas.

## Impact

Updates synthetic schema-copy validation and regression tests; no API or
dependency changes.
