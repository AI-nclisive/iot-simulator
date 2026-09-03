## ADDED Requirements

### Requirement: Manual schemas provide generic Modbus device profiles
The system SHALL provide built-in Modbus TCP manual-schema profiles for a
generic energy meter and generic PLC I/O in addition to the SunSpec inverter.
Each profile's variables SHALL retain explicit zero-based bindings in their
appropriate Modbus data area.

#### Scenario: User materializes a generic energy meter
- **WHEN** a user selects the generic energy-meter profile
- **THEN** the created schema contains named measurement variables at explicit
  holding-register addresses

#### Scenario: User materializes generic PLC I/O
- **WHEN** a user selects the generic PLC I/O profile
- **THEN** the created schema contains explicitly bound coil, discrete-input,
  and holding-register variables
