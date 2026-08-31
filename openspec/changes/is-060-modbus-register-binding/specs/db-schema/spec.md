## ADDED Requirements

### Requirement: Optional explicit Modbus register binding on schema_nodes
`schema_nodes` SHALL have nullable `modbus_register_kind` and
`modbus_address` columns for an explicit, user-pinned Modbus register/coil
override. The two columns SHALL be null together or set together, and
`modbus_register_kind` SHALL be constrained to `COIL`, `DISCRETE_INPUT`,
`HOLDING_REGISTER`, or `INPUT_REGISTER` when set.

#### Scenario: Binding columns default to null
- **WHEN** a schema node is saved with no explicit Modbus binding
- **THEN** both `modbus_register_kind` and `modbus_address` are null

#### Scenario: Setting only one half of the pair is rejected
- **WHEN** an insert or update sets `modbus_register_kind` without
  `modbus_address`, or vice versa
- **THEN** the write fails on the pairing check constraint
