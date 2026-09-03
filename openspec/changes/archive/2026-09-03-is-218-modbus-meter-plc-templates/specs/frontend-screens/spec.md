## ADDED Requirements

### Requirement: Manual Schema creation offers every supported Modbus profile
The Manual Schemas creation dialog SHALL offer each server-supported built-in
Modbus profile as a starting structure without duplicating its register map in
the client.

#### Scenario: Administrator selects a generic profile
- **WHEN** an administrator selects Modbus TCP and a generic energy-meter or
  PLC I/O profile
- **THEN** the dialog sends that profile key to the built-in-profile endpoint
  and opens the resulting schema in the editor
