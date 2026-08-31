## 1. Protocol-model

- [x] 1.1 Add `modbusRegisterKind`/`modbusAddress` to `SchemaNode`, with
      pairing + non-negative + VARIABLE-only validation.
- [x] 1.2 Update every delegating constructor / canonical-constructor call
      site across the codebase for the two new trailing parameters.

## 2. Persistence

- [x] 2.1 Flyway migration: nullable `modbus_register_kind`/`modbus_address`
      columns + pairing/enum check constraints on `schema_nodes`.
- [x] 2.2 `JooqSchemaRepository`: persist and read back the two columns.

## 3. API

- [x] 3.1 `SchemaController.NodeDto` + `ManualSchemaController.NodeDto`:
      add the two fields, with the same pairing validation as the domain
      model, surfaced as a 400 rather than a domain exception.

## 4. Worker contract

- [x] 4.1 `SchemaNodeMsg`: add `modbus_register_kind`/`modbus_address`
      (empty string = unset).
- [x] 4.2 `Supervisor.toProtoSchema`: serialize the two fields (protocol-
      neutral wire mapping, not a supervisor policy change).

## 5. worker-modbus

- [x] 5.1 `ModbusServerRuntime.VarSpec`: add the two optional fields.
- [x] 5.2 Two-pass `layout()`: explicit bindings first (reserving their
      addresses, detecting and falling back on an explicit-vs-explicit
      collision), then default contiguous assignment for everything else,
      skipping reserved addresses.
- [x] 5.3 `ModbusProtocolService.configure()`: read the new `SchemaNodeMsg`
      fields into `VarSpec`.

## 6. Tests

- [x] 6.1 `SchemaNodeModbusBindingTest` (protocol-model): pairing/validation.
- [x] 6.2 `ModbusServerRuntimeIT`: explicit binding honored, auto-assignment
      skips around it.

## 7. Verification

- [x] 7.1 `./gradlew build` green (all modules, including `worker-opcua`).
- [x] 7.2 Confirmed no frontend changes needed/made (backend-only ticket).
