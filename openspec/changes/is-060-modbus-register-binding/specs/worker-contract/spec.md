## ADDED Requirements

### Requirement: Schema wire message carries an optional Modbus register binding
`SchemaNodeMsg` SHALL carry `modbus_register_kind` and `modbus_address`
fields so a worker receives an explicit, persisted register/coil override
over `Configure` without a separate RPC. An empty `modbus_register_kind`
SHALL mean "no override, use the default layout rule" — the same
empty-string-means-unset convention this message already uses for `access`
and `data_type`.

#### Scenario: Empty register kind means no override
- **WHEN** `Configure` sends a `SchemaNodeMsg` with an empty
  `modbus_register_kind`
- **THEN** the worker computes the default contiguous address for that node
  rather than reading `modbus_address`

#### Scenario: Explicit binding reaches the worker unchanged
- **WHEN** `Configure` sends a `SchemaNodeMsg` with a non-empty
  `modbus_register_kind` and a `modbus_address`
- **THEN** the worker projects that node at exactly that register/coil kind
  and address
