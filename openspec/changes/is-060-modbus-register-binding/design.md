## Context
IS-059 implemented `worker-modbus` with only the protocol-model default
register layout (contiguous, schema order) — `protocolBindings.modbus` (the
override the spec already described) had no storage anywhere. This change
adds that storage and threads it through every layer between the DB and the
worker. See `is-059-worker-modbus`'s design.md, Risk: "persisted overrides
deferred to IS-060" — this is that follow-up.

## Goals / Non-Goals

**Goals:**
- Persist an optional, explicit Modbus register/coil binding per schema node.
- Carry it over the worker contract.
- `worker-modbus` honors an explicit binding and keeps auto-assigned nodes
  collision-free around it.

**Non-Goals:**
- A dedicated frontend editor for setting this per node — `[BE]`-only per the
  board; a `UI-XXX` follow-up would add that.
- Validating that an explicit binding's register kind matches the node's
  declared `access` (e.g. rejecting a `HOLDING_REGISTER` binding on a
  `READ`-only node) — the worker honors the binding as given; a mismatch is
  the same "you get what you configured" latitude the default rule already
  has.

## Decisions

**1. Field shape.** Two plain nullable fields on `SchemaNode`
(`modbusRegisterKind: String`, `modbusAddress: Integer`) rather than a
generic `Map<String, Map<String,String>> protocolBindings` keyed by
protocol name. `SchemaNode` already carries several OPC-UA-specific fields
directly (`typeDefinition`, `writeMask`, `historizing`, ...) — this follows
the same precedent rather than introducing a new generic extension
mechanism for a single protocol's single override.

**2. Two-pass layout in `worker-modbus`.** Pass 1 places every explicitly
bound variable and reserves its address(es) per object type; pass 2
auto-assigns every remaining variable, skipping any address pass 1 already
reserved for that object type. This is the minimum change that keeps the
existing default-layout behavior (IS-059) working unmodified for schemas
with no bindings at all, while making bound and unbound variables coexist
safely in the same schema.

**3. No new REST endpoint.** The schema PUT endpoints already replace the
full node list; the two new fields ride along as two more `NodeDto` fields,
consistent with how every other schema-node attribute is exposed.

## Risks / Trade-offs
- **[Risk] No cross-check that an explicit binding's register kind matches
  the node's `access`.** → Accepted (see Non-Goals) — mirrors the existing
  worker-decides-deterministically latitude for the default rule.
- **[Risk] A user could pin two different nodes to the same address by hand
  (both explicit).** → Mitigated at Configure time: pass 1 detects an
  explicit binding that overlaps one already reserved, emits a
  `RuntimeEvent` (`ERROR`), and falls that node through to pass 2's default
  auto-assignment instead of silently overwriting the earlier node's
  process-image entry.

## Migration Plan
Additive Flyway migration (nullable columns + check constraints); existing
rows are unaffected (both new columns null). No rollout sequencing needed —
worker-modbus already treats an absent binding as "use the default", so old
data and new data behave identically until someone sets a binding.
