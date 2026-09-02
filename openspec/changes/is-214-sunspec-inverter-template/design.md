## Context

The manual-schema domain already carries optional explicit Modbus register
bindings, and the Modbus worker honors them in its zero-based process-image
address space. `OpcUaTemplates` is the existing in-process catalog pattern.
See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**

- Supply one vendor-neutral SunSpec profile that a Modbus schema can use
  without manually entering its core register map.
- Keep every materialized field compatible with the types supported by
  `worker-modbus`.

**Non-Goals:**

- Model every SunSpec model, vendor extension, or text field that the current
  Modbus worker cannot materialize.
- Add a template endpoint or a frontend picker change.
- Add per-device byte-order configuration; the worker's established
  big-endian/MSW-first convention applies.

## Decisions

1. **Use the existing explicit holding-register binding fields.** The catalog
   provides `HOLDING_REGISTER` plus a zero-based address for each variable,
   rather than encoding an address in its node ID. This preserves the public
   register map when the manual schema is copied into a data source. The
   alternative, worker-default allocation, would lose SunSpec interoperability.

2. **Expose worker-supported numeric fields from Models 1 and 103.** The
   template includes the `SunS` marker, model identifiers and lengths, the
   Common Model device address, and representative inverter measurements,
   scale factors, energy, status, and event fields. SunSpec's fixed-width text fields are excluded
   because the current worker does not materialize `STRING` values. This keeps
   the profile executable rather than presenting nodes the worker must reject.

3. **Retain raw scaled values and scale factors.** SunSpec engineering values
   are defined by a value plus a signed scale-factor register. Both fields are
   included rather than applying a derived scale in the template, because the
   schema represents protocol values and the worker receives no per-node
   scaling expression.

4. **Use `UINT32` for SunSpec accumulators and event bitfields.** These values
   occupy two holding registers and therefore exercise the worker's established
   MSW-first layout without introducing a separate word-order setting.

## Risks / Trade-offs

- **[Risk] The profile intentionally omits fixed-width SunSpec strings.** →
  Mitigation: retain Model 1 identification fields the worker can serve and
  add text support only with a worker capability change.
- **[Risk] A later SunSpec revision may add fields.** → Mitigation: preserve
  the template's stable node IDs and append a separately reviewed profile
  revision rather than changing existing bindings.

## Migration Plan

The template is static, additive source code. Existing manual schemas and
saved data sources are unchanged; removing the change rolls back by deleting
the catalog without data migration.
