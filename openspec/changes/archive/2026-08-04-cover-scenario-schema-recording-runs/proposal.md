## Why

Re-checking `api-contract` coverage after `cover-remaining-api-endpoints` found
four endpoint groups still unreferenced by any requirement: the whole Scenarios
resource (CRUD, duplicate, validate, run, per-run SSE), a data source's schema
read/replace, recordings CRUD (as opposed to capture and export, which are
covered), and the unified runs resource's list/read/state. These are core
surfaces, not edge cases — scenario authoring and the schema editor are two of
the product's primary flows.

## What Changes

- Add requirements for scenario authoring plus validate-before-run and the
  asynchronous run with its step-event stream.
- Add requirements for whole-schema read/replace with versioning, recordings as a
  browsable and deletable resource, and runs as one unified pollable resource.
- No behavior changes — this documents what already ships.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `api-contract`: requirements added for scenarios (authoring + validation),
  scenario runs (async start + SSE step progress), data-source schema
  read/replace, recordings CRUD, and the unified runs resource.

## Impact

Completes `api-contract` coverage of the current controller surface: every
endpoint group in `api/src/main/java` is now referenced by at least one
requirement.
