## Context

The template picker label is user-facing text, while the folder created from it must meet the manual-schema browse-name validation rules.

## Decision

Add an optional `nodeName` to `StructureTemplate`. Template generation uses it when present and otherwise keeps the existing name-based behaviour. `Tank / vessel` supplies `TankVessel` as its technical name.

This keeps template selection stable and avoids applying a lossy, implicit sanitizer to every template.
