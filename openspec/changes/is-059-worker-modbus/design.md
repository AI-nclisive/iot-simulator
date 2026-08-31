## Context
`workers/worker-modbus` is scaffolded (Gradle module, `j2mod` dependency
already approved and declared) but `ModbusWorkerMain`/`ModbusTypes` are
stubs. `worker-opcua` is the reference implementation of the same
`ProtocolDataSource` contract (`worker-contract/src/main/proto/...`); this
design mirrors its file structure and division of concerns
(`<Proto>ProtocolService` = gRPC controller, `<Proto>ServerRuntime` = the
simulated server, `<Proto>Types` = neutral<->native type mapping).

## Goals / Non-Goals

**Goals:**
- A `ModbusProtocolService` implementing every RPC in the contract, backed by
  a real j2mod `ModbusTCPListener` slave for `Configure`/`Start`/`Stop`/
  `ApplyValues`.
- `ModbusTypes` covering all primitives representable over Modbus registers:
  `BOOL` (coil/discrete input), `INT16`/`UINT16` (single register),
  `INT32`/`UINT32`/`FLOAT32` (register pair, big-endian, most-significant
  register first).
- Default register layout per `protocol-model` §5 (schema order -> contiguous
  address), computed by the worker at `Configure` time — no new persistence.
- `Scan` as an active probe (see decisions) and `Capture` as a poll loop.
- Fault injection (`BAD_VALUE`/`DELAY`/`CONNECTION_DROP`/`MISSING_VALUE`) at
  the same layer as `worker-opcua`'s `project(batch)`.
- Frontend: remove the "hidden until IS-059" gate so Modbus TCP is selectable.

**Non-Goals:**
- Persisted/user-overridable register bindings (`protocolBindings.modbus`) —
  deferred to IS-060. This change only implements the default rule.
- Multiple Modbus function-code variants beyond the standard four object
  types (coils, discrete inputs, holding registers, input registers).
- Device-profile templates (`ModbusTemplates`, e.g. SunSpec) — separate task.
- `FLOAT64`/`INT64`/`UINT64` (4-register spans) — the byte-order convention
  for those is the same, but they are not required for this task's scope;
  `ModbusTypes` documents them as a follow-up rather than guessing.

## Decisions

**1. Register address space model.** A single flat 16-bit address space per
object type (coil/discrete-input/holding-register/input-register), 0-based,
matching j2mod's `ProcessImage` indexing (`ModbusAddressBase` UI field maps
0-based/1-based display to this internally, unaffected by this change).

**2. Multi-register byte/word order.** Big-endian, most-significant register
first (MSW-first) — matches SunSpec and the majority of industrial devices.
Fixed for this change (no per-node override); `ModbusTypes` isolates the
encode/decode so a later override is a small addition, not a rewrite.
Alternative considered: little-endian/LSW-first as default — rejected, it's
the minority convention and would surprise more users by default.

**3. `Scan` semantics.** Modbus has no browsing. The worker (j2mod master)
probes a bounded default range per object type, one read call per contiguous
chunk (mirrors real client behavior — most real client libraries batch reads
rather than one register at a time), treating a Modbus exception response
(`ILLEGAL_DATA_ADDRESS`) as "not present" (excluded) vs. a successful read as
"present" (surfaced as a node). For holding/input registers, after the flat
per-register sweep, a lightweight adjacent-pair heuristic flags candidate
32-bit values (a pair with the second register's value looking like a
fractional/exponent continuation is out of scope for this heuristic — kept
intentionally simple: flag every adjacent pair as a *possible* 32-bit
reinterpretation for user confirmation, default still single-register). This
avoids the worker inventing false structure while still giving the user a
starting point, consistent with the "unknown blocks create until resolved"
pattern already used for OPC UA's unmappable native types.
Alternative considered: only `TestConnection`, no address probing at all,
forcing 100% manual/import schemas — rejected per explicit product direction
("надо анализировать самим возможные узлы и проставлять их параметры").

**4. `Capture` semantics.** j2mod master polls each configured node's address
on a fixed interval (default 500ms; configurable via `Configure`'s
`options` map, same mechanism `worker-opcua` uses for `bindAddress`/
`advertisedHost`), diffing against the last-sent value per node and emitting
only on change — same external contract as OPC UA's push-based capture
(worker-contract delta: "no sampling" reinterpreted as "no sampling below the
poll interval").

**5. `ModbusServerRuntime`.** Wraps j2mod's `SimpleProcessImage` (coils,
discrete inputs, holding/input registers as separate `SimpleRegister`/
`SimpleDigitalIn`/`SimpleDigitalOut` arrays sized to the configured schema's
max address+1) plus a `ModbusTCPListener` bound to loopback/advertised host
per the existing `bindAddress`/`advertisedHost` options convention.
`updateValue(nodeId, decodedValue)` writes into the appropriate array,
mirroring `OpcUaServerRuntime.updateValue`.

## Risks / Trade-offs

- **[Risk] Adjacent-pair 32-bit heuristic produces noisy "maybe" nodes on a
  dense real register map.** → Mitigation: these are advisory only (never
  auto-included in a created schema), and capped by the same `max_nodes`
  scan cap as OPC UA.
- **[Risk] Fixed big-endian/MSW-first order will misdecode a real device that
  uses a different word order, giving a plausible-looking but wrong value.**
  → Mitigation: documented explicitly in `ModbusTypes`' javadoc and the
  proposal's Non-Goals, so a future per-node override change has an obvious
  seam; not silently "best-guessed" per node.
- **[Risk] Poll-based Capture at 500ms can miss a value that changes and
  reverts within one interval.** → Mitigation: this is an inherent Modbus
  protocol limitation (no vendor-neutral change notification exists), not an
  implementation bug; documented in the worker-contract delta rather than
  hidden.

## Migration Plan
No DB migration. Rollout is additive: the Modbus protocol option stays
functionally dead in the UI until this PR unhides it in the same change, so
there is no partial-rollout state to manage. Rollback is a plain revert.
