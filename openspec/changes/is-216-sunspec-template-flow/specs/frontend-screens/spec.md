## ADDED Requirements

### Requirement: Manual Schema creation offers supported Modbus profiles

The Manual Schemas creation dialog SHALL allow an administrator selecting
`Modbus TCP` to choose a supported built-in device profile as its starting
structure. Selecting the SunSpec inverter profile SHALL create the schema
through the built-in-profile API and navigate to its ordinary editor; choosing
the empty option SHALL retain normal blank-schema creation.

#### Scenario: Administrator starts a SunSpec manual schema

- **WHEN** an administrator selects `Modbus TCP`, selects SunSpec Inverter, and
  provides a name
- **THEN** the screen creates the named SunSpec manual schema and opens it in
  the editor
