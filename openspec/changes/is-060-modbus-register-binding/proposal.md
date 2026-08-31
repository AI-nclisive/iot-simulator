## Why
`protocol-model/spec.md` §5 already specifies that a Modbus TCP schema
variable with no explicit `protocolBindings.modbus` gets a worker-computed
default contiguous register address, "surfaced for user review" — but no
storage exists yet for the reviewed/overridden value, so a user can never
actually pin a register address. This closes IS-060: persist an optional
explicit register/coil binding per schema node, carry it over the worker
contract, and have `worker-modbus` honor it instead of the default layout
when present.

## What Changes
- `SchemaNode` (protocol-model) gains two optional fields:
  `modbusRegisterKind` (`COIL | DISCRETE_INPUT | HOLDING_REGISTER |
  INPUT_REGISTER`) and `modbusAddress`, set together or not at all, valid
  only on a `VARIABLE` node.
- New Flyway migration adds nullable `modbus_register_kind`/`modbus_address`
  columns to `schema_nodes`, with a check constraint mirroring the pairing
  rule.
- `SchemaNodeMsg` (worker-contract) carries the same two fields over the wire
  to a worker; empty/default means "no override".
- `worker-modbus`'s layout becomes two-pass: an explicit binding is honored
  verbatim and its address(es) reserved; every variable without one still
  gets the default contiguous address, skipping over whatever the first pass
  reserved so the two never collide.
- Exposed through the existing generic schema REST endpoints (`SchemaController`,
  `ManualSchemaController`) as two more node fields — no new endpoint, since
  schema editing is already a full-document PUT.

Out of scope: a dedicated frontend control for editing this per node (that is
a `UI-XXX` concern); IS-060 is `[BE]`-only per the board.

## Capabilities

### Modified Capabilities
- `protocol-model`: adds the explicit Modbus register-map binding as a
  schema-node-level concept, alongside the already-specified default layout.
- `db-schema`: adds the two nullable `schema_nodes` columns.
- `worker-contract`: `SchemaNodeMsg` carries the new binding fields.

## Impact
- `protocol-model/src/main/java/.../SchemaNode.java` (+ every internal
  delegating constructor).
- `persistence/.../JooqSchemaRepository.java` + new migration.
- `api/.../SchemaController.java`, `api/.../ManualSchemaController.java`
  (`NodeDto`).
- `worker-contract/.../protocol_data_source.proto`.
- `runtime-supervisor/.../Supervisor.java` (neutral schema -> wire mapping;
  protocol-agnostic serialization, not a supervisor policy change).
- `workers/worker-modbus/.../ModbusServerRuntime.java`,
  `ModbusProtocolService.java`.
- A handful of test call sites across `domain`/`runtime-supervisor` updated
  for the two new trailing constructor parameters.
