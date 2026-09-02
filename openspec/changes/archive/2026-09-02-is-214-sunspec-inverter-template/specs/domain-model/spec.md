## ADDED Requirements

### Requirement: Manual schemas have a SunSpec inverter device profile

The system SHALL provide a built-in Modbus TCP manual-schema profile for a
SunSpec three-phase inverter. The profile SHALL contain the `SunS`
beginning-of-models marker, Common Model (1), and Three-Phase Inverter Model
(103) identity, scale-factor, measurement, and status fields represented by
the simulator's supported Modbus value types.
Each variable SHALL retain its public SunSpec holding-register address through
an explicit Modbus binding, using the worker's zero-based address convention.

#### Scenario: A user starts from the SunSpec inverter profile

- **WHEN** a user selects the built-in SunSpec inverter profile while creating
  a Modbus manual schema
- **THEN** the resulting schema contains named Common Model and Model 103
  variables at their defined holding-register addresses

#### Scenario: A two-register inverter field is materialized

- **WHEN** the profile is used to configure a Modbus worker
- **THEN** every selected `UINT32` or `FLOAT32` field reserves its base
  holding-register address and the next address in most-significant-word-first
  order
