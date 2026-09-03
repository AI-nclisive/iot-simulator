## ADDED Requirements

### Requirement: Schema APIs round-trip Modbus register encoding

The data-source schema and manual-schema API node representations SHALL
accept and return optional Modbus byte order, word order, and numeric scale
with a register binding. Invalid order literals and a non-finite or zero scale
SHALL be rejected as validation errors.

#### Scenario: Manual schema saves encoding metadata

- **WHEN** an editor saves a Modbus register variable with valid encoding and
  scale settings
- **THEN** a subsequent GET returns the same settings
