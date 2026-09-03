## ADDED Requirements

### Requirement: Schema wire messages carry Modbus encoding metadata

`SchemaNodeMsg` SHALL carry additive Modbus byte-order, word-order, and scale
fields alongside its optional register binding. An absent/empty order field and
zero-valued unset scale SHALL preserve the worker's established default
conversion.

#### Scenario: Configured encoding reaches a worker

- **WHEN** Configure sends a register-bound node with byte order, word order,
  and scale
- **THEN** the Modbus worker uses those values for server projection and
  capture decoding
