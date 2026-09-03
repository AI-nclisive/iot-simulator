## ADDED Requirements

### Requirement: A Modbus data source persists transport-specific connection settings

A Modbus data source SHALL persist an explicit transport selection. An RTU
selection SHALL persist its serial port identifier, baud rate, data bits,
parity, stop bits, and unit ID separately from any TCP endpoint. Existing
Modbus sources with no transport value SHALL be interpreted as TCP.

#### Scenario: Existing Modbus source remains TCP

- **WHEN** a Modbus data source created before RTU transport support is read
- **THEN** it is treated as a TCP data source without requiring a migration by
  the user
