## Why

The Create Data Source wizard's Schedule calendar can render beneath the
wizard's action panel. This obscures date and time choices at the point a user
needs to configure a recording schedule.

## What Changes

- Render the Schedule date/time picker popup above adjacent wizard panels.
- Preserve the existing inline date/time picker experience and its English
  locale while making every calendar control reachable.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `frontend-screens`: the Create Data Source Wizard Schedule step keeps its
  date/time picker controls usable when the popup overlaps the wizard action
  panel.

## Impact

- Affects the shared Schedule date-picker component and its frontend tests.
- No API, stored-data, or dependency changes.
