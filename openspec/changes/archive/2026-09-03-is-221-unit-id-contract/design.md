## Context

The previous Modbus unit-id delivery stored `#<unitId>` inside
`real_device_endpoint` because the worker protobuf had no separate field. It
is now used by scan, rescan, and capture, which makes the suffix a hidden
cross-layer protocol contract.

## Goals / Non-Goals

**Goals:**

- Store and transmit a typed Modbus unit id independently of the endpoint.
- Preserve existing data by splitting valid legacy suffixes in a Flyway
  migration and defaulting unsuffixed Modbus sources to unit `1`.
- Keep non-Modbus protocols independent of Modbus semantics.

**Non-Goals:**

- Do not change Modbus register layout or Modbus RTU transport.
- Do not require a unit id for simulated Modbus server configuration.

## Decisions

### Persist `real_device_unit_id` alongside the endpoint

`data_sources.real_device_unit_id` is nullable. It is set only for
`MODBUS_TCP`; null means the protocol-independent default unit id `1` at the
boundary where a Modbus client operation is built. A migration splits a final
valid `#0..255` suffix into the new column and the bare endpoint. It defaults
existing unsuffixed Modbus rows to `1`. Invalid suffixes are retained verbatim
and leave the typed column null so they are not silently reinterpreted.

### Add optional proto scalar fields

`TestConnectionRequest`, `ScanRequest`, and `CaptureRequest` receive an
`optional uint32 unit_id`. Optional presence preserves the distinction between
an omitted setting and unit `0`; Modbus receives a resolved value, while OPC UA
does not use it. The supervisor only maps platform specs to the proto and does
not inspect endpoint text.

### Normalize compatibility at repository read/write boundaries

Repository mapping exposes a bare endpoint and a separate unit id. New writes
reject suffix-form Modbus endpoints in the domain service so legacy syntax
cannot re-enter storage. The import path recognizes older exported
suffix-form Modbus values and maps them to the new fields before insertion.

## Risks / Trade-offs

- **[Risk]** A database can contain malformed historical suffix text.
  **Mitigation:** migration does not discard or guess malformed values; the
  normal API validation reports the endpoint as invalid for future use.
- **[Risk]** Proto optional fields regenerate many classes. **Mitigation:** use
  an additive field number and update all client-mode request constructors in
  one change.

## Migration Plan

Add a timestamped Flyway migration because parallel branches may add
migrations. Backfill valid suffixes, strip them from endpoints, and set `1` for
unsuffixed Modbus rows. Rollback is code-only: the retained endpoint column is
still readable, while the new column can be ignored by a prior version.
