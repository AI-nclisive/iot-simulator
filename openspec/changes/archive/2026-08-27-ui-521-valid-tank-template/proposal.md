## Why

The `Tank / vessel` structure template uses its display label as the generated OPC UA browse name. The slash is invalid, so the editor refuses to save a schema created with that template.

## What Changes

- Give structure templates an optional technical node name distinct from their display label.
- Generate the Tank / vessel folder with a valid browse name while preserving its current picker label.
- Cover the template flow with an editor regression test.
