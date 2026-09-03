## ADDED Requirements

### Requirement: Manual schema editor configures Modbus register encoding

For a Modbus TCP manual-schema numeric register variable, the editor SHALL
offer byte order, word order, and scale controls. The controls SHALL preserve
default behavior when left unset and prevent a zero or non-numeric scale from
being saved.

#### Scenario: Author configures a vendor-specific register layout

- **WHEN** an author selects a Modbus register variable and enters
  `LITTLE_ENDIAN`, `LSW_FIRST`, and `0.1`
- **THEN** saving the manual schema includes those encoding settings with that
  variable
