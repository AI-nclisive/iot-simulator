## Why

The `api-contract` baseline covered the main resource groups but left roughly a
third of the shipped REST surface with no requirement at all: the whole Samples
resource, live synthetic runs, rescan, recording schema/values browsing,
recording+sample import/export, derive-synthetic, connected-client and
per-source health queries, the active-runs and project-overview dashboard reads,
admin user management, cross-project admin activity, NodeSet import, and
`/meta`. Uncovered endpoints are the ones that drift first — nothing in the spec
says what they promise, so nothing catches a change that breaks them.

## What Changes

- Add requirements covering every remaining `@RestController` endpoint group, so
  the `api-contract` capability describes the full `/api/v1` surface.
- No behavior changes and no new endpoints — this documents what already ships.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `api-contract`: requirements added for scan materialisation and rescan,
  credential clearing, synthetic source creation and live synthetic runs,
  recording profile derivation, recording schema/values browsing, the shared
  artifact export/download/import shape, samples as a resource, point-in-time
  observability queries (clients, per-source health), the dashboard reads
  (active runs, project overview), admin user management and admin-scoped
  activity, OPC UA NodeSet import, and API metadata.

## Impact

Documentation only. Raises `api-contract` from partial to full coverage of the
current controller surface, so a future change to any of these endpoints has a
requirement to update.
