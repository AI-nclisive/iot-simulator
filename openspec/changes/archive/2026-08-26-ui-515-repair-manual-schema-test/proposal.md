## Why

The manual-schema editor test still targets the removed node-kind radio
control, so the full frontend suite fails even though the editor's direct
`Add data type` action is available.

## What changes

- Update the test to use the current direct control.
- Preserve coverage that data types are created at the schema top level.

## Non-goals

- Do not alter manual-schema editor behavior.
