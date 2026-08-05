## Why

The `document-existing-capabilities` baseline recorded each capability's
known limitations in a trailing `## Known gaps` prose section. `openspec
archive` only merges parsed `### Requirement:` blocks into the main spec, so
**all six `## Known gaps` sections were silently dropped** on archive. The
live specs are therefore now stricter than the code: they assert behavior the
system does not have, with no caveat. Two requirements are also plainly wrong
against the code (the nav item list, and `user` being able to "start" a data
source when no bare start endpoint exists).

A limitation has to be expressed as normative requirement text to survive
archive. That is also the honest form: a spec describes what the system does
today, so "X is only supported for Y" is a requirement, not a footnote.

## What Changes

- State each dropped limitation as normative requirement text on the affected
  capability, so it survives archive and reads as current behavior.
- Correct `frontend-shell`'s navigation requirement to the real nav list
  (`Activity` is not in it; the label is `Recordings`, not
  `Recordings & Samples`), and record that `/activity` is reachable by URL
  only — as a stated limitation rather than a violated SHALL.
- Correct `auth-modes` so the `user` role's runtime scope matches
  `api-contract` (there is no bare data-source `start`).
- Correct `frontend-screens`' page inventory label to match the shipped page.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `protocol-model`: `DATA_TYPE` nodes are model- and manual-schema-level only;
  the normalized `schema_nodes` storage does not accept them yet.
- `worker-contract`: the schema message sent to a worker carries the original
  folder/variable field set only, so extended node kinds cannot cross the wire.
- `db-schema`: the real `schema_nodes.kind` constraint set, and the tables that
  exist but no application code uses.
- `api-contract`: application liveness/readiness is Actuator-only; project and
  environment settings endpoints do not exist.
- `auth-modes`: `user` runtime scope corrected (no bare source start).
- `frontend-shell`: real nav list; `/activity` is URL-reachable only.
- `frontend-screens`: page inventory label corrected; retention/cleanup page
  design still pending.

## Impact

Documentation only — no code changes. Fixes `openspec/specs/` accuracy so the
baseline can be trusted as the contract it is meant to be.
