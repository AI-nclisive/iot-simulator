## Context

IS-216 established server-side profile materialization, but it currently
hardcodes the SunSpec key in both the service and UI.

## Decisions

- Keep register definitions in `ModbusTemplates`; callers pass only a stable
  template key.
- Use generic profile names and explicit zero-based bindings, separating
  Modbus data areas so coil/input/register offsets never collide within an
  area.
- Keep the UI display map separate from register definitions; it never copies
  node/address data.

## Risks

- Profile changes must preserve stable keys. Tests assert representative
  bindings and area-local non-overlap.
